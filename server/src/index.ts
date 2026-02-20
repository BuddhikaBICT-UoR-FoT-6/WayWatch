// Load environment variables as early as possible
require('dotenv').config();

import express = require('express');
import type { Request, Response } from 'express';
import cors = require('cors');
import mongoose from 'mongoose';
import axios from 'axios';
import bcrypt = require('bcrypt');
import { z } from 'zod';
import { requireAuth, signAccessToken, type AuthedRequest, signRefreshToken, verifyRefreshToken, verifyTokenMiddleware, rotateRefreshToken, revokeRefreshTokenByJti, revokeAllTokensForUser } from './auth';
import { User, type UserRole } from './models/User';
import { RefreshTokenModel } from './models/RefreshToken';
import { requireRole } from './roles';
import { loginLimiter, registerLimiter, trafficSamplesLimiter, getRecentRateLimitBlocks, listBlockedKeys, removeBlockedKey } from './middleware/rateLimiter';
import { duplicateSubmissionProtection } from './middleware/duplicateDetection';
import tomtomDebugRoute from './routes/tomtomDebugRoute';
import trafficRoute from './routes/trafficRoute';
import searchRoute from './routes/searchRoute';
import { startTomTomScheduler } from './tasks/tomtomScheduler';


const app = express();
app.use(cors());
// Set a conservative global JSON body limit to protect against huge payloads (1MB)
app.use(express.json({ limit: '1mb' }));

// Respect proxy headers (X-Forwarded-For) when behind a proxy/load-balancer.
// Set to true for generic setups; in production you may prefer a restricted list
// of trusted proxies or IP ranges for tighter security.
app.set('trust proxy', true);

if(!process.env.REDIS_URL){
  console.warn('WARNING: REDIS_URL is NOT configured — rate limiter will use in-memory fallback and duplicate detection will not work. Configure REDIS_URL for production.');
}

const MONGODB_URI = process.env.MONGODB_URI;
const TOMTOM_API_KEY = process.env.TOMTOM_API_KEY;

if (!MONGODB_URI) {
  console.error('Missing MONGODB_URI in environment');
  process.exit(1);
}

if(!TOMTOM_API_KEY){
    console.warn('WARNING: TOMTOM_API_KEY is NOT configured — TomTom debug route will not work. Configure TOMTOM_API_KEY for production.')
}

// Mongo connection
mongoose.set('strictQuery', true);
mongoose
  .connect(MONGODB_URI)
  .then(() => console.log('MongoDB connected'))
  .catch((err) => {
    console.error('MongoDB connection error:', err);
    process.exit(1);
  });

// Schemas
const SAMPLE_RETENTION_DAYS = Number(process.env.SAMPLE_RETENTION_DAYS || '30');

const SampleSchema = new mongoose.Schema(
  {
    routeId: { type: String, required: true, index: true },
    windowStartMs: { type: Number, required: true, index: true },
    segmentId: { type: String, required: true, index: true },
    severity: { type: Number, required: true, min: 0, max: 5 },
    reportedAtMs: { type: Number, required: true, index: true },
    userIdHash: { type: String },
    lat: { type: Number },
    lon: { type: Number },
    createdAt: { type: Date, required: true, default: () => new Date(), index: true },
  },
  { timestamps: false, versionKey: false }
);
// TTL index to automatically remove old samples
if (SAMPLE_RETENTION_DAYS > 0) {
  const seconds = SAMPLE_RETENTION_DAYS * 24 * 60 * 60;
  SampleSchema.index({ createdAt: 1 }, { expireAfterSeconds: seconds });
}

const AggregateSchema = new mongoose.Schema(
  {
    routeId: { type: String, required: true, index: true },
    windowStartMs: { type: Number, required: true, index: true },
    segmentId: { type: String, required: true, index: true },
    severityAvg: { type: Number, required: true },
    severityP50: { type: Number },
    severityP90: { type: Number },
    sampleCount: { type: Number, required: true },
    lastAggregatedAtMs: { type: Number, required: true },
  },
  { timestamps: false, versionKey: false }
);
AggregateSchema.index({ routeId: 1, windowStartMs: 1, segmentId: 1 }, { unique: true });

const Sample = mongoose.model('Sample', SampleSchema);
const Aggregate = mongoose.model('Aggregate', AggregateSchema);

// Export models so other scripts (aggregator) can import them
export { Sample, Aggregate };

// Validation schemas
const submitSampleSchema = z.object({
  routeId: z.string().min(1),
  windowStartMs: z.number().int().positive(),
  segmentId: z.string().default('_all'),
  severity: z.number().min(0).max(5),
  reportedAtMs: z.number().int().positive(),
  userIdHash: z.string().optional(),
  lat: z.number().optional(),
  lon: z.number().optional(),
});

const aggregateWindowSchema = z.object({
  routeId: z.string().min(1),
  windowStartMs: z.number().int().positive(),
  segmentId: z.string().default('_all'),
});

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
});

const refreshSchema = z.object({
  refreshToken: z.string().min(1),
});

const createUserSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  role: z.enum(['superadmin', 'admin', 'user']).default('user'),
});

const updateUserRoleSchema = z.object({
  role: z.enum(['superadmin', 'admin', 'user']),
});

const providerQuery = z.object({
    lat: z.string().transform(Number),
    lon: z.string().transform(Number),
});

function computeStats(values: number[]) {
  const sorted = [...values].sort((a, b) => a - b);
  const len = sorted.length;
  const avg = sorted.reduce((s, v) => s + v, 0) / len;
  const p = (q: number) => sorted[Math.floor(q * (len - 1))];
  return { avg, p50: p(0.5), p90: p(0.9), count: len };
}

// for map function
function mapSpeedToSeverity(speedKmph: number | null){
    // lower speed = higher severity
    if(speedKmph === null || speedKmph == undefined) return 3;
    if(speedKmph < 10) return 5;
    if(speedKmph < 20) return 4;
    if(speedKmph < 30) return 3;
    if(speedKmph < 40) return 2;
    if(speedKmph < 50) return 1;
    return 0;
}

async function fetchTomTomFlow(lat: number, lon: number){
    // TomTom flow segment data endpoint
    const url = `https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json`;
    const params = {
        point: `${lat},${lon}`,
        unit: 'KMPH',
        key: TOMTOM_API_KEY
    };
    const res = await axios.get(url, {params, timeout: 5000});
    return res.data;
}

// Export helper for aggregator scripts
export { computeStats };

// Health check (useful for emulator connectivity testing)
app.get('/health', (_req: Request, res: Response) => {
  res.json({ ok: true });
});

// Mount TomTom debug route (provides GET /api/v1/debug/provider/point)
app.use(tomtomDebugRoute);
// Mount traffic routes (GET /api/v1/traffic and POST /api/v1/traffic/bbox)
app.use(trafficRoute);
// Mount search routes (GET /api/v1/search and GET /api/v1/reverse)
app.use(searchRoute);

// --- Auth routes ---
app.post('/api/v1/auth/register', registerLimiter, async (req: Request, res: Response) => {
  try {
    const { email, password } = registerSchema.parse(req.body);

    const existing = await User.findOne({ email: email.toLowerCase() }).lean();
    if (existing) {
      return res.status(409).json({ ok: false, error: 'Email already registered' });
    }

    const passwordHash = await bcrypt.hash(password, 12);
    const created = await User.create({ email: email.toLowerCase(), passwordHash, role: 'user' as UserRole });

    // Enforce single-active-refresh-token: revoke any previous tokens linked to this user (precaution)
    await revokeAllTokensForUser(String(created._id));

    const accessToken = signAccessToken({ id: String(created._id), email: created.email, role: String(created.get('role')) as UserRole });
    const { token: refreshToken } = await signRefreshToken(String(created._id));
    // don't store per-user hashed refreshToken - using RefreshTokenModel as source of truth

    return res.status(201).json({
      ok: true,
      data: {
        accessToken,
        refreshToken,
        user: { id: String(created._id), email: created.email, role: String(created.get('role')) },
      },
    });
  } catch (e: any) {
    console.error('/auth/register error', e);
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.post('/api/v1/auth/login', loginLimiter, async (req: Request, res: Response) => {
  try {
    const { email, password } = loginSchema.parse(req.body);

    const user = await User.findOne({ email: email.toLowerCase() });
    if (!user) {
      return res.status(401).json({ ok: false, error: 'Invalid email or password' });
    }

    const ok = await bcrypt.compare(password, String(user.get('passwordHash')));
    if (!ok) {
      return res.status(401).json({ ok: false, error: 'Invalid email or password' });
    }

    // Revoke existing refresh tokens for this user to enforce single-active-token policy
    await revokeAllTokensForUser(String(user._id));

    const accessToken = signAccessToken({
      id: String(user._id),
      email: String(user.get('email')),
      role: String(user.get('role') || 'user') as UserRole,
    });
    const { token: refreshToken } = await signRefreshToken(String(user._id));
    // don't store per-user hashed refreshToken - using RefreshTokenModel as source of truth

    return res.json({
      ok: true,
      data: {
        accessToken,
        refreshToken,
        user: { id: String(user._id), email: String(user.get('email')), role: String(user.get('role') || 'user') },
      },
    });
  } catch (e: any) {
    console.error('/auth/login error', e);
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.post('/api/v1/auth/refresh', async (req: Request, res: Response) => {
  try {
    const { refreshToken } = refreshSchema.parse(req.body);
    const decoded = await verifyRefreshToken(refreshToken);

    const user = await User.findById(decoded.sub);
    if (!user) {
      return res.status(401).json({ ok: false, error: 'Invalid refresh token' });
    }

    // rotate: will verify+revoke old and issue a new refresh token
    const newRefreshToken = await rotateRefreshToken(refreshToken, { ip: req.ip, ua: req.headers['user-agent'] });

    const accessToken = signAccessToken({
      id: String(user._id),
      email: String(user.get('email')),
      role: String(user.get('role') || 'user') as UserRole,
    });

    return res.json({ ok: true, data: { accessToken, refreshToken: newRefreshToken } });
  } catch (e: any) {
    console.error('/auth/refresh error', e);
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.post('/api/v1/auth/logout', requireAuth, async (req: AuthedRequest, res: Response) => {
  try {
    const { refreshToken } = req.body || {};
    if (refreshToken) {
      // try to revoke by jti from token
      try {
        const decoded = await verifyRefreshToken(refreshToken);
        await revokeRefreshTokenByJti(decoded.jti);
      } catch (e) {
        // ignore invalid token
      }
    } else {
      // If no token supplied, revoke all tokens for the current user (optional behavior)
      await RefreshTokenModel.updateMany({ userId: req.user!.sub }, { $set: { revoked: true } });
    }
    return res.json({ ok: true });
  } catch (e: any) {
    console.error('/auth/logout error', e);
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

// --- Admin/Superadmin user management ---
// Superadmin: list all users
app.get('/api/v1/admin/users', requireAuth, requireRole(['superadmin']), async (_req: AuthedRequest, res: Response) => {
  const users = await User.find({}).select('email role createdAt').lean();
  return res.json({ ok: true, data: users });
});

// Admin+Superadmin: create regular users; Superadmin can create admins/superadmins
app.post('/api/v1/admin/users', requireAuth, requireRole(['admin', 'superadmin']), async (req: AuthedRequest, res: Response) => {
  try {
    const { email, password, role } = createUserSchema.parse(req.body);

    const requestedRole = role as UserRole;
    const callerRole = req.user!.role;

    if (callerRole !== 'superadmin' && requestedRole !== 'user') {
      return res.status(403).json({ ok: false, error: 'Only superadmin can create admin/superadmin accounts' });
    }

    const normalizedEmail = email.toLowerCase();
    const existing = await User.findOne({ email: normalizedEmail }).lean();
    if (existing) {
      return res.status(409).json({ ok: false, error: 'Email already registered' });
    }

    const passwordHash = await bcrypt.hash(password, 12);
    const created = await User.create({ email: normalizedEmail, passwordHash, role: requestedRole });

    return res.status(201).json({
      ok: true,
      data: { id: String(created._id), email: String(created.get('email')), role: String(created.get('role')) },
    });
  } catch (e: any) {
    console.error('/admin/users POST error', e);
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

// Superadmin only: update a user's role
app.patch('/api/v1/admin/users/:id/role', requireAuth, requireRole(['superadmin']), async (req: AuthedRequest, res: Response) => {
  try {
    const { id } = req.params;
    const { role } = updateUserRoleSchema.parse(req.body);
    const updated = await User.findByIdAndUpdate(
      id,
      { $set: { role } },
      { new: true }
    ).select('email role createdAt');

    if (!updated) {
      return res.status(404).json({ ok: false, error: 'User not found' });
    }

    return res.json({ ok: true, data: { id: String(updated._id), email: String(updated.get('email')), role: String(updated.get('role')) } });
  } catch (e: any) {
    console.error('/admin/users/:id/role PATCH error', e);
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

// Admin endpoint: recent rate-limit blocks (superadmin only)
app.get('/api/v1/admin/rate-limits', requireAuth, requireRole(['superadmin']), async (_req: AuthedRequest, res: Response) => {
  try {
    const items = await getRecentRateLimitBlocks(200);
    return res.json({ ok: true, data: items });
  } catch (e: any) {
    console.error('/admin/rate-limits error', e);
    return res.status(500).json({ ok: false, error: 'Failed to fetch rate-limit blocks' });
  }
});

// Admin endpoint: list temporary blocklist entries (superadmin only)
app.get('/api/v1/admin/blocks', requireAuth, requireRole(['superadmin']), async (_req: AuthedRequest, res: Response) => {
  try {
    const items = await listBlockedKeys(200);
    return res.json({ ok: true, data: items });
  } catch (e: any) {
    console.error('/admin/blocks error', e);
    return res.status(500).json({ ok: false, error: 'Failed to list blocked keys' });
  }
});

// Admin endpoint: remove/unblock a key (superadmin only)
app.delete('/api/v1/admin/blocks/:key', requireAuth, requireRole(['superadmin']), async (req: AuthedRequest, res: Response) => {
  try {
    const key = String(req.params.key || '').trim();
    if (!key) return res.status(400).json({ ok: false, error: 'Missing key' });
    const ok = await removeBlockedKey(key);
    if (!ok) return res.status(404).json({ ok: false, error: 'Key not found or removal failed' });
    return res.json({ ok: true });
  } catch (e: any) {
    console.error('/admin/blocks DELETE error', e);
    return res.status(500).json({ ok: false, error: 'Failed to remove block' });
  }
});

// Routes
app.post(
  '/api/v1/samples',
  requireAuth,
  trafficSamplesLimiter,
  duplicateSubmissionProtection(60 * 5),
  async (req: AuthedRequest, res: Response) => {
    try {
      const body = req.body;
      const MAX_SAMPLES = 1000; // per-request limit

      const samplesArray = Array.isArray(body) ? body : [body];
      if (samplesArray.length === 0) {
        return res.status(400).json({ ok: false, error: 'Empty payload' });
      }
      if (samplesArray.length > MAX_SAMPLES) {
        return res.status(413).json({ ok: false, error: `Too many samples - max ${MAX_SAMPLES}` });
      }

      // Validate each sample using submitSampleSchema
      const parsed: any[] = [];
      for (const s of samplesArray) {
        const p = submitSampleSchema.parse(s);
        parsed.push(p);
      }

      // Bulk insert
      await Sample.insertMany(parsed, { ordered: false });
      res.status(201).json({ ok: true, inserted: parsed.length });
    } catch (e: any) {
      console.error('/samples error', e);
      // Zod validation errors will be caught here
      res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
    }
  },
);


app.post('/api/v1/aggregate', requireAuth, async (req: AuthedRequest, res: Response) => {
  try {
    const { routeId, windowStartMs, segmentId } = aggregateWindowSchema.parse(req.body);

    const samples = await Sample.find({ routeId, windowStartMs, segmentId }).select('severity');
    if (samples.length === 0) {
      return res.json({ ok: true, message: 'No samples to aggregate' });
    }
    const severities = samples.map((s) => Number(s.severity));
    const stats = computeStats(severities);

    const doc = {
      routeId,
      windowStartMs,
      segmentId,
      severityAvg: stats.avg,
      severityP50: stats.p50,
      severityP90: stats.p90,
      sampleCount: stats.count,
      lastAggregatedAtMs: Date.now(),
    };

    await Aggregate.updateOne(
      { routeId, windowStartMs, segmentId },
      { $set: doc },
      { upsert: true }
    );

    res.json({ ok: true, data: doc });
  } catch (e: any) {
    console.error('/aggregate error', e);
    res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.get('/api/v1/aggregates', requireAuth, async (req: AuthedRequest, res: Response) => {
  try {
    const routeId = String(req.query.routeId || '').trim();
    const windowStartMs = Number(req.query.windowStartMs || 0);
    if (!routeId || !Number.isFinite(windowStartMs) || windowStartMs <= 0) {
      return res.status(400).json({ ok: false, error: 'Invalid parameters' });
    }

    const results = await Aggregate.find({ routeId, windowStartMs }).lean();
    res.json({ ok: true, data: results });
  } catch (e: any) {
    console.error('/aggregates error', e);
    res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});


// Public reporting endpoint (convenience wrapper for UI clients).
// Validates payload and inserts into samples collection. This mirrors /api/v1/samples behavior
// but can be used as a client-friendly endpoint (without requiring auth token).
app.post('/api/v1/report', async (req: Request, res: Response) => {
  try {
    const body = req.body;
    const p = submitSampleSchema.parse(body);
    await Sample.create(p);
    return res.status(201).json({ ok: true, inserted: 1 });
  } catch (e: any) {
    console.error('/api/v1/report error', e?.message || e);
    return res.status(400).json({ ok: false, error: e?.message || 'Bad Request' });
  }
});

// Export app for testing and reuse
export { app };

// Only start listening when run directly
if (require.main === module) {
  const PORT = Number(process.env.PORT || 3000);
  const HOST = process.env.HOST || '0.0.0.0';

  const server = app.listen(PORT, HOST, () => {
    console.log(`Server listening on http://${HOST}:${PORT}`);
  });

  server.on('error', (err: any) => {
    if (err && err.code === 'EADDRINUSE') {
      console.error(`Port ${PORT} is already in use. Stop the existing process or start with a different PORT.`);
      console.error('Example: set PORT=3001 then run npm start');
      process.exit(1);
    }
    console.error('Server failed to start:', err);
    process.exit(1);
  });
}


/**
 * Protect traffic samples submissions:
 * - verify token (must be authenticated)
 * - per-user rate limit
 * - duplicate detection
 */
app.post(
  '/traffic/samples',
  verifyTokenMiddleware,         // must set req.user
  trafficSamplesLimiter,         // per-user + per-ip limits
  duplicateSubmissionProtection(60 * 5), // 5min duplicate TTL
  async (req: AuthedRequest, res: Response) => {
    try {
      const body = req.body;
      const MAX_SAMPLES = 1000; // per-request limit

      const samplesArray = Array.isArray(body) ? body : [body];
      if (samplesArray.length === 0) {
        return res.status(400).json({ ok: false, error: 'Empty payload' });
      }
      if (samplesArray.length > MAX_SAMPLES) {
        return res.status(413).json({ ok: false, error: `Too many samples - max ${MAX_SAMPLES}` });
      }

      // Validate each sample using submitSampleSchema and attach userIdHash if missing
      const parsed: any[] = [];
      for (const s of samplesArray) {
        const p = submitSampleSchema.parse(s);
        // Prefer explicit userIdHash from payload; if missing, attach authenticated user id
        if (!p.userIdHash && req.user && req.user.sub) {
          p.userIdHash = String(req.user.sub);
        }
        parsed.push(p);
      }

      // Bulk insert into samples collection
      await Sample.insertMany(parsed, { ordered: false });
      res.status(201).json({ ok: true, inserted: parsed.length });
    } catch (e: any) {
      console.error('/traffic/samples error', e);
      // Zod validation errors or other issues
      res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
    }
  },
);

app.get('/api/v1/debug/provider/point', async (req, res) => {
    try{
        const q = providerQuery.parse(req.query);
        if(!TOMTOM_API_KEY){
            return res.status(400).json({ok: false, error: 'TOMTOM_API_KEY not configured'});
        }

        const raw = await fetchTomTomFlow(q.lat, q.lon);
        // Try to extract currentSpeed from response in typical TomTom shape.
        let speed: number | null = null;

        try{
            // typical path: flowSegmentData -> RWS -> RW -> FIS -> FI -> CF -> SP (varies). Use safe navigation.
            if(raw && raw.flowSegmentData && raw.flowSegmentData.currentSpeed != null){
                speed = Number(raw.flowSegmentData.currentSpeed);
            } else if(raw && raw.flowSegmentData && raw.flowSegmentData.freeFlowSpeed) {
                // fallback
                speed = Number(raw.flowSegmentData.freeFlowSpeed);
            }
        } catch(e) { speed = null; }

        const severity = mapSpeedToSeverity(speed);
        return res.json({ ok: true, provider: raw, mapped: { speedKmph: speed, severity }});

    } catch(e: any){
        console.error('/debug/provider/point error', e?.message || e);
        return res.status(400).json({ok: false, error: e?.message || 'Bad Request'});
    }
});


// Only start TomTom scheduler when explicitly enabled in env
if (process.env.ENABLE_TOMTOM_SCHEDULER === 'true') {
  try {
    startTomTomScheduler();
    console.log('TomTom scheduler enabled');
  } catch (e) {
    console.error('Failed to start TomTom scheduler', (e as any)?.message || e);
  }
}
