import type { Config } from 'jest';

const config: Config = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  testTimeout: 20000,
  testMatch: ['**/__tests__/**/*.test.ts'],
  clearMocks: true,
};

export default config;
