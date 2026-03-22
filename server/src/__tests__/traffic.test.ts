/**
 * Unit tests for traffic route helpers (no external API / DB required).
 */

// Map speedKmph -> severity (mirrors index.ts logic; tested independently here)
function mapSpeedToSeverity(speedKmph: number | null): number {
  if (speedKmph === null || speedKmph === undefined) return 3;
  if (speedKmph < 10) return 5;
  if (speedKmph < 20) return 4;
  if (speedKmph < 30) return 3;
  if (speedKmph < 40) return 2;
  if (speedKmph < 50) return 1;
  return 0;
}

describe('mapSpeedToSeverity', () => {
  const cases: [number | null, number][] = [
    [null, 3],
    [0, 5],
    [5, 5],
    [10, 4],
    [19, 4],
    [20, 3],
    [29, 3],
    [30, 2],
    [39, 2],
    [40, 1],
    [49, 1],
    [50, 0],
    [100, 0],
  ];

  test.each(cases)('speed %p -> severity %p', (speed, expected) => {
    expect(mapSpeedToSeverity(speed)).toBe(expected);
  });
});

describe('sample validation', () => {
  it('rejects severity out of range', () => {
    const { z } = require('zod');
    const schema = z.object({ severity: z.number().min(0).max(5) });
    expect(() => schema.parse({ severity: 6 })).toThrow();
    expect(() => schema.parse({ severity: -1 })).toThrow();
    expect(() => schema.parse({ severity: 3 })).not.toThrow();
  });
});
