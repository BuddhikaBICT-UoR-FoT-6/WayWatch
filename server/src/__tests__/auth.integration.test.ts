import mongoose from 'mongoose';
import request from 'supertest';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { app } from '../index';
import { User } from '../models/User';

jest.setTimeout(20000);

describe('Auth integration', () => {
  let mongod: MongoMemoryServer;
  beforeAll(async () => {
    mongod = await MongoMemoryServer.create();
    const uri = mongod.getUri();
    await mongoose.connect(uri);
  });

  afterAll(async () => {
    await mongoose.disconnect();
    await mongod.stop();
  });

  afterEach(async () => {
    await User.deleteMany({});
  });

  test('register -> login -> refresh -> logout -> revoked reuse', async () => {
    const email = 'test@example.com';
    const password = 'password123';

    // register
    const reg = await request(app).post('/api/v1/auth/register').send({ email, password });
    expect(reg.status).toBe(201);
    const refresh1 = reg.body.data.refreshToken;
    expect(refresh1).toBeDefined();

    // login (should revoke previous tokens and issue new)
    const log = await request(app).post('/api/v1/auth/login').send({ email, password });
    expect(log.status).toBe(200);
    const refresh2 = log.body.data.refreshToken;
    expect(refresh2).toBeDefined();
    expect(refresh2).not.toBe(refresh1);

    // use refresh2 to get new access
    const refRes = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: refresh2 });
    expect(refRes.status).toBe(200);
    const newAccess = refRes.body.data.accessToken;
    expect(newAccess).toBeDefined();

    // logout with refresh2
    const agent = request.agent(app);
    // need to include a valid access token to call logout (requireAuth)
    const loginForAccess = await request(app).post('/api/v1/auth/login').send({ email, password });
    const accessToken = loginForAccess.body.data.accessToken;
    expect(accessToken).toBeDefined();

    const logoutRes = await agent.post('/api/v1/auth/logout').set('Authorization', `Bearer ${accessToken}`).send({ refreshToken: refresh2 });
    expect(logoutRes.status).toBe(200);

    // attempt to reuse refresh2 should fail
    const reuse = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: refresh2 });
    expect(reuse.status).toBe(400);
  });
});

