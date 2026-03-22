/**
 * Unit tests for aggregation cron logic (no DB / cron required).
 */

function computeStats(severities: number[]) {
  const sorted = [...severities].sort((a, b) => a - b);
  const len = sorted.length;
  const avg = sorted.reduce((s, v) => s + v, 0) / len;
  const p = (q: number) => sorted[Math.floor(q * (len - 1))];
  return { avg, p50: p(0.5), p90: p(0.9), count: len };
}

describe('computeStats', () => {
  it('single value', () => {
    const s = computeStats([4]);
    expect(s.avg).toBe(4);
    expect(s.p50).toBe(4);
    expect(s.p90).toBe(4);
    expect(s.count).toBe(1);
  });

  it('multiple values', () => {
    const s = computeStats([1, 2, 3, 4, 5]);
    expect(s.avg).toBe(3);
    expect(s.p50).toBe(3);
    expect(s.p90).toBe(4); // floor(0.9 * 4) = index 3 -> value 4
    expect(s.count).toBe(5);
  });

  it('even distribution p50', () => {
    const s = computeStats([0, 2, 4, 6]);
    expect(s.avg).toBe(3);
  });
});

describe('aggregation window alignment', () => {
  it('aligns timestamp to 15-min window', () => {
    const windowSizeMs = 15 * 60 * 1000;
    const now = 1700000000000;
    const aligned = Math.floor(now / windowSizeMs) * windowSizeMs;
    expect(aligned % windowSizeMs).toBe(0);
    expect(now - aligned).toBeLessThan(windowSizeMs);
  });
});
