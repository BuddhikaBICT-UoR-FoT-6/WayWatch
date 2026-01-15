import mongoose from 'mongoose';

export type UserRole = 'superadmin' | 'admin' | 'user';

export type UserDoc = {
  _id: mongoose.Types.ObjectId;
  email: string;
  passwordHash: string;
  role: UserRole;
  refreshTokenHash?: string;
  refreshTokenIssuedAt?: Date;
  createdAt: Date;
};

const UserSchema = new mongoose.Schema(
  {
    email: { type: String, required: true, unique: true, index: true, lowercase: true, trim: true },
    passwordHash: { type: String, required: true },
    role: { type: String, required: true, enum: ['superadmin', 'admin', 'user'], default: 'user', index: true },
    refreshTokenHash: { type: String, required: false },
    refreshTokenIssuedAt: { type: Date, required: false },
    createdAt: { type: Date, default: Date.now },
  },
  { versionKey: false }
);

export const User = mongoose.model('User', UserSchema);
