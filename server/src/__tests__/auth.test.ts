/**
 * Unit tests for auth helpers (no DB / Redis required).
 */

// Set env BEFORE any imports that read process.env at module load time
process.env.JWT_SECRET = 'test-secret-at-least-32-chars-long!!';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-at-least-32!!';
process.env.LOCKOUT_MAX_ATTEMPTS = '3';
process.env.LOCKOUT_WINDOW_SECONDS = '60';
process.env.LOCKOUT_DURATION_SECONDS = '120';
process.env.NODE_ENV = 'test';
process.env.MONGODB_URI = 'mongodb://localhost/test';

jest.mock('../middleware/rateLimiter', () => ({ redisClient: null }));
jest.mock('../models/RefreshToken', () => ({
  RefreshTokenModel: {
    findOneAndUpdate: jest.fn().mockResolvedValue({}),
    findOne: jest.fn().mockResolvedValue(null),
    updateOne: jest.fn().mockResolvedValue({}),
  },
}));

import { signAccessToken, isAccountLocked, recordFailedLogin, clearLockout } from '../auth';

describe('signAccessToken', () => {
  it('returns a JWT string', () => {
    const token = signAccessToken({ id: 'u1', email: 'a@b.com', role: 'user' });
    expect(typeof token).toBe('string');
    expect(token.split('.').length).toBe(3);
  });
});

describe('account lockout (in-memory fallback)', () => {
  const email = 'locktest@example.com';

  afterEach(async () => {
    await clearLockout(email);
  });

  it('not locked initially', async () => {
    expect(await isAccountLocked(email)).toBe(false);
  });

  it('locks after LOCKOUT_MAX_ATTEMPTS failures', async () => {
    await recordFailedLogin(email);
    await recordFailedLogin(email);
    expect(await isAccountLocked(email)).toBe(false);
    await recordFailedLogin(email);
    expect(await isAccountLocked(email)).toBe(true);
  });

  it('clears lockout', async () => {
    await recordFailedLogin(email);
    await recordFailedLogin(email);
    await recordFailedLogin(email);
    expect(await isAccountLocked(email)).toBe(true);
    await clearLockout(email);
    expect(await isAccountLocked(email)).toBe(false);
  });
});
