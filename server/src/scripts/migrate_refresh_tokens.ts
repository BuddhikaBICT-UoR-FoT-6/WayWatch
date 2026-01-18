import mongoose from 'mongoose';
import dotenv from 'dotenv';
import { RefreshTokenModel } from '../models/RefreshToken';
import { DEFAULT_REFRESH_EXPIRES } from '../auth';

dotenv.config();

async function parseDurationToMs(d: string) {
  const s = String(d);
  const m = /^([0-9]+)(s|m|h|d)$/.exec(s);
  if (!m) return Number(s) * 24 * 60 * 60 * 1000;
  const v = Number(m[1]);
  switch (m[2]) {
    case 's': return v * 1000;
    case 'm': return v * 60 * 1000;
    case 'h': return v * 60 * 60 * 1000;
    case 'd': return v * 24 * 60 * 60 * 1000;
    default: return v * 1000;
  }
}

async function run() {
  const MONGODB_URI = process.env.MONGODB_URI;
  if (!MONGODB_URI) throw new Error('MONGODB_URI missing');

  await mongoose.connect(MONGODB_URI);
  console.log('Connected to Mongo for refresh token migration');

  // Ensure TTL index exists on expiresAt
  await RefreshTokenModel.collection.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });
  console.log('Ensured TTL index on expiresAt');

  // Backfill documents that lack expiresAt
  const ms = await parseDurationToMs(DEFAULT_REFRESH_EXPIRES);
  const now = new Date();
  const cursor = RefreshTokenModel.find({ expiresAt: { $exists: false } }).cursor();
  let updated = 0;
  for (let doc = await cursor.next(); doc != null; doc = await cursor.next()) {
    const issuedAt = (doc as any).issuedAt ? new Date((doc as any).issuedAt) : now;
    const expiresAt = new Date(issuedAt.getTime() + ms);
    await RefreshTokenModel.updateOne({ _id: (doc as any)._id }, { $set: { expiresAt } });
    updated++;
  }
  console.log('Backfilled', updated, 'docs');

  await mongoose.disconnect();
  console.log('Migration complete');
}

run().catch((e) => {
  console.error(e);
  process.exit(1);
});

