import dotenv from 'dotenv';
import mongoose from 'mongoose';
import bcrypt from 'bcrypt';
import { User, type UserRole } from './models/User';

dotenv.config();

async function main() {
  const MONGODB_URI = process.env.MONGODB_URI;
  if (!MONGODB_URI) {
    console.error('Missing MONGODB_URI in environment');
    process.exit(1);
  }

  await mongoose.connect(MONGODB_URI);

  const users: Array<{ email: string; password: string; role: UserRole }> = [
    { email: 'superadmin@ceylonqueue.com', password: 'SuperAdmin@123', role: 'superadmin' },
    { email: 'admin@ceylonqueue.com', password: 'Admin@12345', role: 'admin' },
    { email: 'user1@ceylonqueue.com', password: 'User@12345', role: 'user' },
    { email: 'user2@ceylonqueue.com', password: 'User@12345', role: 'user' },
  ];

  for (const u of users) {
    const email = u.email.toLowerCase();
    const existing = await User.findOne({ email });
    const passwordHash = await bcrypt.hash(u.password, 12);

    if (existing) {
      existing.set('passwordHash', passwordHash);
      existing.set('role', u.role);
      await existing.save();
      console.log(`Updated: ${email} (${u.role})`);
    } else {
      await User.create({ email, passwordHash, role: u.role });
      console.log(`Created: ${email} (${u.role})`);
    }
  }

  await mongoose.disconnect();
}

main().catch(async (e) => {
  console.error('Seed failed:', e);
  try {
    await mongoose.disconnect();
  } catch {
    // ignore
  }
  process.exit(1);
});

