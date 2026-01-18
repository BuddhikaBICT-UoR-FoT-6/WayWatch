import type { Request, Response, NextFunction } from 'express';
import jwt, { type SignOptions, type Secret, type JwtPayload } from 'jsonwebtoken';
import { User, type UserRole } from './models/User';
import crypto from 'crypto';
import { RefreshTokenModel } from './models/RefreshToken';

export const DEFAULT_ACCESS_EXPIRES = process.env.JWT_EXPIRES_IN || '15m';
export const DEFAULT_REFRESH_EXPIRES = process.env.JWT_REFRESH_EXPIRES_IN || '30d';
const ACCESS_SECRET = process.env.JWT_SECRET;
const REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || process.env.JWT_SECRET; // fallback for compatibility

if (!ACCESS_SECRET) {
  throw new Error('Missing JWT_SECRET (access token secret) in environment');
}

// In production, require a distinct refresh secret for better isolation
if (process.env.NODE_ENV === 'production' && (!process.env.JWT_REFRESH_SECRET || process.env.JWT_REFRESH_SECRET === process.env.JWT_SECRET)) {
  throw new Error('In production, please set a separate JWT_REFRESH_SECRET that is different from JWT_SECRET');
}

if (!REFRESH_SECRET) {
  throw new Error('Missing JWT_REFRESH_SECRET or JWT_SECRET (refresh token secret) in environment');
}

// JWT payload for access tokens
export type JwtUser = {
  sub: string;
  email: string;
  role: UserRole;
};

// JWT payload for refresh tokens
export type JwtRefresh = {
  sub: string;
  jti: string;
};

// Small parser: supports '15m', '30d', '3600s' etc -> milliseconds
function parseDurationToMs(d: string): number {
  if (!d) return 0;
  const s = String(d).trim();
  const m = /^([0-9]+)(s|m|h|d)$/.exec(s);
  if (!m) {
    // try numeric days
    const n = Number(s);
    if (!Number.isFinite(n)) return 0;
    return n * 24 * 60 * 60 * 1000;
  }
  const v = Number(m[1]);
  switch (m[2]) {
    case 's': return v * 1000;
    case 'm': return v * 60 * 1000;
    case 'h': return v * 60 * 60 * 1000;
    case 'd': return v * 24 * 60 * 60 * 1000;
    default: return v * 1000;
  }
}

// Sign a short-lived access token
export function signAccessToken(user: { id: string; email: string; role: UserRole }): string {
  const payload: JwtUser = { sub: user.id, email: user.email, role: user.role };
  const opts: SignOptions = {
    algorithm: 'HS256',
    expiresIn: DEFAULT_ACCESS_EXPIRES as SignOptions['expiresIn'],
  };
  return jwt.sign(payload, ACCESS_SECRET as Secret, opts);
}

// Sign a refresh token, persist a record with jti/issuedAt/expiresAt/meta and return token+jti
export async function signRefreshToken(userId: string, meta?: Record<string, any>): Promise<{ token: string; jti: string }> {
  // generate a random jti
  const jti = crypto.randomBytes(16).toString('hex');
  const payload: JwtRefresh = { sub: userId, jti };
  const opts: SignOptions = {
    algorithm: 'HS256',
    expiresIn: DEFAULT_REFRESH_EXPIRES as SignOptions['expiresIn'],
  };
  const token = jwt.sign(payload, REFRESH_SECRET as Secret, opts);

  // compute expiresAt timestamp as Date
  const now = new Date();
  const expiresAt = new Date(now.getTime() + parseDurationToMs(DEFAULT_REFRESH_EXPIRES));

  // persist the refresh token record for lifecycle management
  await RefreshTokenModel.create({ userId, jti, issuedAt: now, expiresAt, revoked: false, meta: meta || {} });

  // Enforce TTL index will remove expired tokens automatically
  return { token, jti };
}

// Verify a refresh token and ensure its jti exists and is not revoked. Throws on invalid.
export async function verifyRefreshToken(token: string): Promise<JwtRefresh> {
  const decoded = jwt.verify(token, REFRESH_SECRET as Secret) as JwtPayload & JwtRefresh;
  const jti = String(decoded.jti || '');
  const sub = String(decoded.sub || '');
  if (!jti || !sub) throw new Error('Invalid refresh token payload');

  const doc = await RefreshTokenModel.findOne({ jti }).lean();
  if (!doc) throw new Error('Refresh token not found');
  if ((doc as any).revoked) throw new Error('Refresh token revoked');
  if (String((doc as any).userId) !== String(sub)) throw new Error('Token user mismatch');

  return { sub, jti } as JwtRefresh;
}

// Revoke a refresh token by its jti (marks revoked=true)
export async function revokeRefreshTokenByJti(jti: string): Promise<void> {
  await RefreshTokenModel.updateOne({ jti }, { $set: { revoked: true } });
}

// Revoke all tokens for a user
export async function revokeAllTokensForUser(userId: string): Promise<void> {
  await RefreshTokenModel.updateMany({ userId }, { $set: { revoked: true } });
}

// Rotate refresh token: verify old token, revoke its jti, issue a new refresh token record and return token
export async function rotateRefreshToken(oldToken: string, meta?: Record<string, any>): Promise<string> {
  const payload = await verifyRefreshToken(oldToken);
  // revoke previous token record
  await revokeRefreshTokenByJti(payload.jti);
  // issue new refresh token
  const { token: newToken } = await signRefreshToken(payload.sub, meta);
  return newToken;
}

// Express middleware to require a valid access token
export type AuthedRequest = Request & { user?: JwtUser };
export function requireAuth(req: AuthedRequest, res: Response, next: NextFunction) {
  try {
    const header = String(req.headers.authorization || '');
    const token = header.startsWith('Bearer ') ? header.slice('Bearer '.length) : '';
    if (!token) {
      return res.status(401).json({ ok: false, error: 'Missing token' });
    }
    const decoded = jwt.verify(token, ACCESS_SECRET as Secret) as JwtUser;
    req.user = decoded;
    next();
  } catch (e) {
    return res.status(401).json({ ok: false, error: 'Invalid token' });
  }
}

// Export alias used elsewhere
export const verifyTokenMiddleware = requireAuth;

// Issue access + refresh tokens together (used after login/register)
export async function issueTokens(userId: string, email: string, role: UserRole, meta?: Record<string, any>) {
  const access = signAccessToken({ id: userId, email, role });
  const { token: refreshToken, jti } = await signRefreshToken(userId, meta);
  return { accessToken: access, refreshToken, jti };
}

// Refresh endpoint helper: exchange refresh token for a new access token (does not rotate)
export async function refreshTokenHandler(req: Request, res: Response) {
  const { token } = req.body;
  try {
    const payload = await verifyRefreshToken(token);
    const newAccess = jwt.sign({ sub: payload.sub }, ACCESS_SECRET as Secret, { expiresIn: DEFAULT_ACCESS_EXPIRES as SignOptions['expiresIn'] });
    res.json({ jwt: newAccess });
  } catch (err: any) {
    res.status(401).send(String(err?.message || 'Invalid refresh token'));
  }
}

// Logout helper: revoke refresh token by token string (find jti then revoke)
export async function logoutHandler(req: Request, res: Response) {
  const { token } = req.body;
  try {
    const decoded = jwt.verify(token, REFRESH_SECRET as Secret) as JwtPayload & JwtRefresh;
    const jti = String(decoded.jti || '');
    if (jti) await revokeRefreshTokenByJti(jti);
  } catch (e) {
    // ignore invalid token on logout
  }
  res.sendStatus(200);
}
