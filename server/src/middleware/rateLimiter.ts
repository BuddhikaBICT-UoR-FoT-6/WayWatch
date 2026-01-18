import { Request, Response, NextFunction } from 'express';
import { RateLimiterRedis, RateLimiterMemory } from 'rate-limiter-flexible';
import Redis from 'ioredis';

/**
 * Centralized rate limiter factory using Redis when available,
 * fallback to in-memory limiter for dev/tests.
 *
 * Each limiter returns an Express middleware that:
 * - produces 429 responses on exceed
 * - sets `Retry-After` and `X-RateLimit-*` headers when possible
 * - optionally logs blocked events into Redis for admin inspection
 */

 /* Create Redis client (optional). Ensure REDIS_URL is set in production. */
 const redisUrl = process.env.REDIS_URL || null;
 let redisClient: any = null;

if(redisUrl){
    redisClient = new Redis(redisUrl);
}

/* Configuration for blocklist behavior (strikes -> block) */
const BLOCK_STRIKE_THRESHOLD = Number(process.env.RL_BLOCK_STRIKES || '10');
const BLOCK_TTL_SECONDS = Number(process.env.RL_BLOCK_TTL_SEC || String(60 * 60)); // default 1 hour
const STRIKE_WINDOW_SECONDS = Number(process.env.RL_STRIKE_WINDOW_SEC || String(60 * 60)); // window to count strikes

/* small duration parser: accepts '1h','30m','60s','24h' or plain seconds string/number */
function parseDurationToSeconds(duration: string): number {
    if (!duration) return 1;
    if (/^\d+$/.test(duration)) return Number(duration);
    const m = /^([0-9]+)(s|m|h|d)$/.exec(duration.trim());
    if (!m) return Math.max(1, parseInt(duration) || 1);
    const v = Number(m[1]);
    switch (m[2]) {
        case 's': return v;
        case 'm': return v * 60;
        case 'h': return v * 3600;
        case 'd': return v * 86400;
        default: return Math.max(1, v);
    }
}

/* Helper to build a limiter instance or fallback */
function buildLimiter(points: number, durationSeconds: number, keyPrefix: string){
    if(redisClient){
        return new RateLimiterRedis({
            storeClient: redisClient,
            points,
            duration: durationSeconds,
            keyPrefix,
        });
    } else {
        // Non-production fallback
        return new RateLimiterMemory({
            points,
            duration: durationSeconds,
            keyPrefix,
        });
    }
}

/* In-memory blocklist fallback (per-process) */
const inMemoryBlocklist = new Map<string, number>(); // key -> expiresAt (ms)
function setInMemoryBlock(key: string, ttlSec: number){
    const until = Date.now() + ttlSec * 1000;
    inMemoryBlocklist.set(key, until);
    // schedule removal
    setTimeout(() => { inMemoryBlocklist.delete(key); }, ttlSec * 1000 + 1000);
}
function isInMemoryBlocked(key: string){
    const v = inMemoryBlocklist.get(key);
    if(!v) return false;
    if(Date.now() > v){ inMemoryBlocklist.delete(key); return false; }
    return true;
}

/* Blocklist helpers (Redis-backed preferred) */
async function isBlockedKey(key: string): Promise<{blocked:boolean, ttl?:number}>{
    try{
        if(!redisClient) return { blocked: isInMemoryBlocked(key), ttl: undefined };
        const exists = await redisClient.exists(`block:${key}`);
        if(!exists) return { blocked: false };
        const ttl = await redisClient.ttl(`block:${key}`);
        return { blocked: exists === 1, ttl: ttl >= 0 ? ttl : undefined };
    } catch (e){
        return { blocked: isInMemoryBlocked(key), ttl: undefined };
    }
}

async function addStrikeAndMaybeBlock(key: string){
    try{
        if(!redisClient){
            // fallback: increment a per-process counter in a Map with expiry using redis-like keys
            const strikeKey = `strike:${key}`;
            // store strike counts in inMemoryBlocklist by reusing map as strike store (simple)
            const existing = (inMemoryBlocklist.get(strikeKey) || 0) as any;
            const strikes = (typeof existing === 'number' ? existing : 0) + 1;
            // store strikes as expiresAt negative to differentiate from real blocks
            inMemoryBlocklist.set(strikeKey, strikes);
            if(strikes >= BLOCK_STRIKE_THRESHOLD){
                // block in-memory
                setInMemoryBlock(key, BLOCK_TTL_SECONDS);
                inMemoryBlocklist.delete(strikeKey);
            } else {
                // set a timeout to clear the strike count after window
                setTimeout(() => { inMemoryBlocklist.delete(strikeKey); }, STRIKE_WINDOW_SECONDS * 1000 + 1000);
            }
            return;
        }

        // Use Redis: INCR strike counter with expiry (STRIKE_WINDOW_SECONDS)
        const strikeKey = `strike:${key}`;
        const strikes = await redisClient.incr(strikeKey);
        if(strikes === 1){
            await redisClient.expire(strikeKey, STRIKE_WINDOW_SECONDS);
        }
        if(strikes >= BLOCK_STRIKE_THRESHOLD){
            // set block key with TTL
            await redisClient.set(`block:${key}`, '1', 'EX', BLOCK_TTL_SECONDS);
            // optional: delete strikes counter
            await redisClient.del(strikeKey);
        }
    } catch (e){
        // ignore
    }
}

/* Retrieve blocked keys (Redis) */
export async function listBlockedKeys(limit = 200){
    if(!redisClient){
        // return in-memory blocked keys
        const now = Date.now();
        const arr: any[] = [];
        for(const [k, v] of inMemoryBlocklist.entries()){
            if(k.startsWith('block:')){
                const ttl = Math.max(0, Math.round(((v as number) - now) / 1000));
                arr.push({ key: k.replace(/^block:/, ''), ttl });
            }
        }
        return arr.slice(0, limit);
    }
    try{
        const keys = await redisClient.keys('block:*');
        const sliced = keys.slice(0, Math.min(limit, keys.length));
        const results = [];
        for(const k of sliced){
            const ttl = await redisClient.ttl(k);
            results.push({ key: String(k).replace(/^block:/, ''), ttl: ttl >= 0 ? ttl : undefined });
        }
        return results;
    } catch (e){
        return [];
    }
}

export async function removeBlockedKey(key: string){
    if(!redisClient){
        return inMemoryBlocklist.delete(`block:${key}`);
    }
    try{
        await redisClient.del(`block:${key}`);
        return true;
    } catch (e){
        return false;
    }
}

/* Utility to send 429 with headers and optionally log the blocked key to Redis */
function sendTooMany(req: Request, res: Response, msBeforeNext: number, remaining?: number, total?: number, key?: string){
    const retryAfterSec = Math.ceil(msBeforeNext / 1000);
    res.setHeader('Retry-After', String(retryAfterSec));

    if(typeof remaining === 'number' && typeof total === 'number'){
        res.setHeader('X-RateLimit-Limit', String(total));
        res.setHeader('X-RateLimit-Remaining', String(Math.max(0, remaining)));
    }

    // Log a short record of the blocked event in Redis (sorted set) for admin UI
    try {
      if (redisClient && redisClient.zadd) {
        const entry = JSON.stringify({
          key: key || 'unknown',
          path: (req as any).originalUrl || req.url,
          when: Date.now(),
          ip: req.ip || (req.headers['x-forwarded-for'] as string) || req.socket.remoteAddress,
        });
        // score by timestamp so we can range query by recency
        redisClient.zadd('rate-limit-blocks', Date.now(), entry).catch(() => {});
        // keep only the most recent 2000 entries
        redisClient.zremrangebyrank('rate-limit-blocks', 0, -2001).catch(() => {});
      }
    } catch (e) {
      // non-fatal
    }

    // Add strike and possibly create block if threshold exceeded
    if(key){
      void addStrikeAndMaybeBlock(key);
    }

    return res.status(429).json({
        error: 'Too Many Requests',
        retryAfterSeconds: retryAfterSec,
    });
}

/* Check blocklist and optionally short-circuit */
async function checkAndShortCircuitBlocked(req: Request, res: Response, key: string | null){
    if(!key) return false;
    const { blocked, ttl } = await isBlockedKey(String(key));
    if(blocked){
        // short-circuit with Forbidden and Retry-After equal to TTL
        const retry = ttl ?? 60;
        res.setHeader('Retry-After', String(retry));
        return res.status(403).json({ error: 'Temporarily blocked due to repeated abuse', retryAfterSeconds: retry });
    }
    return false;
}

/* Build middleware that keys by IP */
export function ipRateLimitMiddleware(points: number, duration: string){
    const durationSec = Math.max(1, Math.round(parseDurationToSeconds(duration)));
    const limiter = buildLimiter(points, durationSec, `rl:ip:${points}:${durationSec}`);

    return async (req: Request, res: Response, next: NextFunction) => {
        const key = req.ip || (req.headers['x-forwarded-for'] as string) || req.socket.remoteAddress || 'unknown-ip';

        // check blocklist first
        if(await checkAndShortCircuitBlocked(req, res, key)) return;

        try{
            const rlRes = await limiter.consume(String(key));
            res.setHeader('X-RateLimit-Limit', String(points));
            res.setHeader('X-RateLimit-Remaining', String(Math.max(0, rlRes.remainingPoints)));
            return next();
        } catch (rejRes: any){
            return sendTooMany(req, res, rejRes.msBeforeNext, rejRes.remainingPoints, points, key);
        }
    };
}

/* Build middleware that keys by identifier (user id, email, username). Accepts a function to extract key. */
export function identifierRateLimitMiddleware(points: number, duration: string, getIdentifier: (req: Request) => string | null){
    const durationSec = Math.max(1, Math.round(parseDurationToSeconds(duration)));

    const limiter = buildLimiter(points, durationSec, `rl:id:${points}:${durationSec}`);
    return async (req: Request, res: Response, next: NextFunction) => {
         const id = getIdentifier(req) || (req.ip || 'anon');

         // check blocklist first
         if(await checkAndShortCircuitBlocked(req, res, String(id))) return;

         try {
           const rlRes = await limiter.consume(String(id));
           res.setHeader('X-RateLimit-Limit', String(points));
           res.setHeader('X-RateLimit-Remaining', String(Math.max(0, rlRes.remainingPoints)));
           return next();
         } catch (rejRes: any) {
           return sendTooMany(req, res, rejRes.msBeforeNext, rejRes.remainingPoints, points, id);
         }
     };
}

/* Combined middleware: check both ipLimiter and identifierLimiter in parallel (fail on either). */
export function combinedIpAndIdentifierMiddleware(
    ipConfig: {points: number; duration: string},
    idConfig: {points: number; duration: string},
    getIdentifier: (req: Request) => string | null
){
    const ipLimiter = ipRateLimitMiddleware(ipConfig.points, ipConfig.duration);
    const idLimiter = identifierRateLimitMiddleware(idConfig.points, idConfig.duration, getIdentifier);

    return async(req: Request, res: Response, next: NextFunction) => {
        // run identifier first (so username/email checks block early for /login)
        await new Promise<void>((resolve) => {
            idLimiter(req, res, () => resolve());
                  // if idLimiter ended the request, the promise will not resolve and route will exit.

    }).catch(() => {}); // not expected because idLimiter handles responses

    // If response already sent by idLimiter, stop
    if(res.headersSent) return;

    await new Promise<void>((resolve) => {
        ipLimiter(req, res, () => resolve());
    }).catch(() => {});

    if(res.headersSent) return;
    return next();

    };
}

/* Retrieve recent rate-limit blocks from Redis (returns parsed JSON records). */
export async function getRecentRateLimitBlocks(limit = 200) {
  if (!redisClient || !redisClient.zrevrange) return [];
  try {
    const items = await redisClient.zrevrange('rate-limit-blocks', 0, Math.min(limit - 1, 1999));
    return items.map((s: string) => {
      try { return JSON.parse(s); } catch (e) { return { raw: s }; }
    });
  } catch (e) {
    return [];
  }
}

/* Pre-built common middlewares for convenience (tunable) */
export const loginLimiter = combinedIpAndIdentifierMiddleware(
    { points: 100, duration: '1h' }, // per-IP (total attempts per hour)
    { points: 5, duration: '1h' },   // per-username/email per hour (strict)

    (req: Request) => {
        // If user not authenticated, extract username/email from body if present
        // adjust keys to lower-case so they unify
        const body = req.body || {};
        const email = (body.email || body.username || body.user || '').toString().toLowerCase();
        return email || null;
    },
);

export const registerLimiter = combinedIpAndIdentifierMiddleware(
  { points: 50, duration: '1h' }, // per-IP registrations
  { points: 3, duration: '24h' }, // per-email or per-phone per day
  (req: Request) => {
    const body = req.body || {};
    return (body.email || body.phone || '').toString().toLowerCase() || null;
  },
);

/* Traffic samples: require authenticated user key; strict per-user and looser per-IP */
export const trafficSamplesLimiter = combinedIpAndIdentifierMiddleware(
    { points: 200, duration: '1h' }, // per-IP global upper bound
    { points: 60, duration: '1h' },  // per-user allowed submissions per hour
    (req: Request) => {
        // Expect your auth layer to set req.user.id or req.user.sub
        // Fallback to token or ip if missing
        const anyUser: any = (req as any).user || {};
        return String(anyUser.id || anyUser.sub || req.ip || 'anon-user') || null;
    },
);

/* Export redis client for duplicate detection or other middleware if needed */
export { redisClient };
