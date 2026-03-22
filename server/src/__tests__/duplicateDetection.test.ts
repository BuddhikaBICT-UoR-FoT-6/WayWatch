/**
 * Unit tests for duplicate detection middleware.
 */
import type { Request, Response } from 'express';

// Mock redis client
const mockRedis: any = { set: jest.fn() };
jest.mock('../middleware/rateLimiter', () => ({ redisClient: mockRedis }));

import { duplicateSubmissionProtection } from '../middleware/duplicateDetection';

function makeReqRes(body: any, userId?: string) {
  const req = { body, ip: '127.0.0.1', user: userId ? { sub: userId } : undefined } as unknown as Request;
  const res = {
    status: jest.fn().mockReturnThis(),
    json: jest.fn().mockReturnThis(),
    setHeader: jest.fn(),
    headersSent: false,
  } as unknown as Response;
  const next = jest.fn();
  return { req, res, next };
}

describe('duplicateSubmissionProtection', () => {
  beforeEach(() => jest.clearAllMocks());

  it('calls next when Redis returns OK (new submission)', async () => {
    mockRedis.set.mockResolvedValue('OK');
    const mw = duplicateSubmissionProtection(300);
    const { req, res, next } = makeReqRes({ routeId: 'r1', severity: 3 }, 'user1');
    await mw(req, res, next);
    expect(next).toHaveBeenCalled();
  });

  it('returns 429 when Redis returns null (duplicate)', async () => {
    mockRedis.set.mockResolvedValue(null);
    const mw = duplicateSubmissionProtection(300);
    const { req, res, next } = makeReqRes({ routeId: 'r1', severity: 3 }, 'user1');
    await mw(req, res, next);
    expect(next).not.toHaveBeenCalled();
    expect(res.status).toHaveBeenCalledWith(429);
  });

  it('fails open on Redis error', async () => {
    mockRedis.set.mockRejectedValue(new Error('redis down'));
    const mw = duplicateSubmissionProtection(300);
    const { req, res, next } = makeReqRes({ routeId: 'r1', severity: 3 }, 'user1');
    await mw(req, res, next);
    expect(next).toHaveBeenCalled();
  });
});
