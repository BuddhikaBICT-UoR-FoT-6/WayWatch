import type { Response, NextFunction } from 'express';
import type { AuthedRequest } from './auth';
import type { UserRole } from './models/User';

export function requireRole(roles: UserRole[]) {
  return (req: AuthedRequest, res: Response, next: NextFunction) => {
    const role = req.user?.role;
    if (!role) {
      return res.status(401).json({ ok: false, error: 'Unauthorized' });
    }
    if (!roles.includes(role)) {
      return res.status(403).json({ ok: false, error: 'Forbidden' });
    }
    next();
  };
}

