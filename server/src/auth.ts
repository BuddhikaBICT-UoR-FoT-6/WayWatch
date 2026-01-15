import type { Request, Response, NextFunction } from 'express';
import jwt, { type SignOptions } from 'jsonwebtoken';
import type { UserRole } from './models/User';
import crypto from 'crypto';

export type JwtUser = {
  sub: string;
  email: string;
  role: UserRole;
};

export type JwtRefresh = {
  sub: string;
  jti: string;
};

export function getJwtSecret(): string {
  const secret = process.env.JWT_SECRET;
  if (!secret) {
    throw new Error('Missing JWT_SECRET in environment');
  }
  return secret;
}

export function signAccessToken(user: { id: string; email: string; role: UserRole }): string {
  const payload: JwtUser = { sub: user.id, email: user.email, role: user.role };

  const opts: SignOptions = {
    algorithm: 'HS256',
    expiresIn: (process.env.JWT_EXPIRES_IN || '7d') as SignOptions['expiresIn'],
  };

  return jwt.sign(payload, getJwtSecret(), opts);
}

export function signRefreshToken(userId: string): { token: string; jti: string } {
  const jti = crypto.randomBytes(16).toString('hex');
  const payload: JwtRefresh = { sub: userId, jti };
  const opts: SignOptions = {
    algorithm: 'HS256',
    expiresIn: (process.env.JWT_REFRESH_EXPIRES_IN || '30d') as SignOptions['expiresIn'],
  };
  return { token: jwt.sign(payload, getJwtSecret(), opts), jti };
}

export function verifyRefreshToken(token: string): JwtRefresh {
  return jwt.verify(token, getJwtSecret()) as JwtRefresh;
}

export type AuthedRequest = Request & { user?: JwtUser };

export function requireAuth(req: AuthedRequest, res: Response, next: NextFunction) {
  try {
    const header = String(req.headers.authorization || '');
    const token = header.startsWith('Bearer ') ? header.slice('Bearer '.length) : '';
    if (!token) {
      return res.status(401).json({ ok: false, error: 'Missing token' });
    }

    const decoded = jwt.verify(token, getJwtSecret()) as JwtUser;
    req.user = decoded;
    next();
  } catch (e) {
    return res.status(401).json({ ok: false, error: 'Invalid token' });
  }
}
