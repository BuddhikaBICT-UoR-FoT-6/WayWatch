import { Request, Response, NextFunction } from 'express';
import crypto from 'crypto';
import { redisClient } from './rateLimiter';

/**
 * Detect duplicate submissions for `POST /traffic/samples`.
 * Hash the normalized request body + user id, store a lock in Redis with TTL.
 * If the same hash appears again within TTL, reject as duplicate spam.
 *
 * TTL and behavior are configurable.
*/

const DEFAULT_TTL_SECONDS = 60 * 5;
const FAIL_CLOSED_ON_NO_REDIS = process.env.DUP_DETECT_FAIL_CLOSED === '1';

function hashPayload(userId: string, body: any){
    const normalized = JSON.stringify(body); // stable ordering
    return crypto.createHash('sha256').update(userId + ':' + normalized).digest('hex');
}

export function duplicateSubmissionProtection(ttlSeconds = DEFAULT_TTL_SECONDS){
  // If Redis is missing and FAIL_CLOSED_ON_NO_REDIS is true, reject to avoid spam when Redis is down.
  return async(req: Request, res: Response, next: NextFunction) => {
    const anyUser: any = (req as any).user || {};
    const userId = String(anyUser.id || anyUser.sub || req.ip || 'anon');

    const key = `dup:${userId}:${hashPayload(userId, req.body)}`;

    try{
        if(!redisClient){
            // No Redis - default behavior is to fail open (allow) but log. Controlled by env var.
            console.warn('duplicateDetection: redis not available');
            if(FAIL_CLOSED_ON_NO_REDIS){
                res.setHeader('Retry-After', String(ttlSeconds));
                return res.status(429).json({
                    error: 'Duplicate detection unavailable (fail-closed)',
                    retryAfterSeconds: ttlSeconds,
                });
            }
            return next();
        }
        // SET key NX EX ttl -> returns 'OK' if set, or null if exists
        const setResult = await redisClient.set(key, '1', 'NX', 'EX', ttlSeconds);
        if(setResult === null){
            // Duplicate within TTL
            res.setHeader('Retry-After', String(ttlSeconds));
            return res.status(429).json({
                error: 'Duplicate or too-frequent submission detected',
                retryAfterSeconds: ttlSeconds,
            });
        }
        return next();
    } catch(err){
      // On Redis error, fail open but log server-side
      console.error('duplicateDetection error', err);
      if(FAIL_CLOSED_ON_NO_REDIS){
        res.setHeader('Retry-After', String(ttlSeconds));
        return res.status(429).json({
            error: 'Duplicate detection error (fail-closed)',
            retryAfterSeconds: ttlSeconds,
        });
      }
      return next();

    }

  }

}
