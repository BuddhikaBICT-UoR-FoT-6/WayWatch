// TypeScript
import mongoose from 'mongoose';

// Store refresh tokens with jti and expiration; expiresAt is a Date so we can add a TTL index
const RefreshTokenSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    jti: { type: String, required: true, index: true, unique: true },
    issuedAt: { type: Date, required: true },
    expiresAt: { type: Date, required: true, index: true },
    revoked: { type: Boolean, default: false, index: true },
    meta: { type: Object }, // optional metadata (ip, userAgent)
  },
  { timestamps: false, versionKey: false }
);

// TTL index: expire documents when expiresAt <= now
RefreshTokenSchema.index({ expiresAt: 1 }, { expireAfterSeconds: 0 });

export const RefreshTokenModel = mongoose.model('RefreshToken', RefreshTokenSchema);
export type RefreshTokenDoc = mongoose.Document & {
  userId: string;
  jti: string;
  issuedAt: Date;
  expiresAt: Date;
  revoked: boolean;
  meta?: Record<string, any>;
};
