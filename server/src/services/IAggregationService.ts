import { Sample, Aggregate, computeStats } from '../index';

export interface IAggregationService {
  aggregateWindow(routeId: string, windowStartMs: number, segmentId: string): Promise<any>;
}

export class AggregationService implements IAggregationService {
  async aggregateWindow(routeId: string, windowStartMs: number, segmentId: string): Promise<any> {
    const samples = await Sample.find({ routeId, windowStartMs, segmentId }).select('severity');
    if (samples.length === 0) {
      return null;
    }
    const severities = samples.map((s) => Number(s.severity));
    const stats = computeStats(severities);

    const doc = {
      routeId,
      windowStartMs,
      segmentId,
      severityAvg: stats.avg,
      severityP50: stats.p50,
      severityP90: stats.p90,
      sampleCount: stats.count,
      lastAggregatedAtMs: Date.now(),
    };

    await Aggregate.updateOne(
      { routeId, windowStartMs, segmentId },
      { $set: doc },
      { upsert: true }
    );

    return doc;
  }
}
