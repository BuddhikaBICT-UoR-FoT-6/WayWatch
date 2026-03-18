import type { Request, Response, NextFunction } from 'express';

export function internalOnly(req: Request, res: Response, next: NextFunction) {
  const internalSecret = process.env.INTERNAL_SECRET;
  if (!internalSecret) {
    return res.status(500).json({ ok: false, error: 'INTERNAL_SECRET not configured' });
  }

  const key = req.headers['x-internal-key'];
  if (key !== internalSecret) {
    return res.status(403).json({ ok: false, error: 'Forbidden: Invalid internal key' });
  }

  next();
}
