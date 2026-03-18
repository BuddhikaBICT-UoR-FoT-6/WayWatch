import { z } from 'zod';

const baseSchema = z.object({
  MONGODB_URI: z.string().min(1, 'MONGODB_URI is required'),
  JWT_SECRET: z.string().min(32, 'JWT_SECRET must be at least 32 characters'),
  JWT_REFRESH_SECRET: z.string().min(32, 'JWT_REFRESH_SECRET must be at least 32 characters').optional(),
  PORT: z.string().default('3000'),
  HOST: z.string().default('0.0.0.0'),
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development').transform(v => v.trim() as 'development' | 'production' | 'test'),
  REDIS_URL: z.string().url().optional(),
  INTERNAL_SECRET: z.string().min(1).optional(),
  TOMTOM_API_KEY: z.string().optional(),
  SAMPLE_RETENTION_DAYS: z.string().default('30'),
  LOG_LEVEL: z.enum(['error', 'warn', 'info', 'debug']).default('info'),
  ENABLE_TOMTOM_SCHEDULER: z.enum(['true', 'false']).default('false'),
  RATE_LIMIT_DISABLED: z.enum(['true', 'false']).default('false'),
  REDIS_DISABLED: z.enum(['true', 'false']).default('false'),
  LOCKOUT_MAX_ATTEMPTS: z.string().default('5'),
  LOCKOUT_WINDOW_SECONDS: z.string().default('900'),
  LOCKOUT_DURATION_SECONDS: z.string().default('1800'),
});

const productionSchema = baseSchema.superRefine((data, ctx) => {
  if (data.NODE_ENV === 'production') {
    if (!data.REDIS_URL) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'REDIS_URL is required in production', path: ['REDIS_URL'] });
    }
    if (!data.INTERNAL_SECRET) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'INTERNAL_SECRET is required in production', path: ['INTERNAL_SECRET'] });
    }
    if (!data.JWT_REFRESH_SECRET || data.JWT_REFRESH_SECRET === data.JWT_SECRET) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'JWT_REFRESH_SECRET must be set and differ from JWT_SECRET in production', path: ['JWT_REFRESH_SECRET'] });
    }
  }
});

export type AppConfig = z.infer<typeof baseSchema>;

let _config: AppConfig | null = null;

export function validateConfig(): AppConfig {
  if (_config) return _config;

  const result = productionSchema.safeParse(process.env);
  if (!result.success) {
    const issues = result.error.issues.map(i => `  • ${i.path.join('.')}: ${i.message}`).join('\n');
    throw new Error(`Fatal: Invalid server configuration:\n${issues}`);
  }
  _config = result.data;
  return _config;
}

export function getConfig(): AppConfig {
  if (!_config) throw new Error('Config not initialised — call validateConfig() first');
  return _config;
}
