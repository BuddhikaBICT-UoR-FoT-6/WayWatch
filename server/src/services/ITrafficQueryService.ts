import { Aggregate } from '../index';
import { TrafficSummaryDto, toTrafficSummaryDto } from '../models/TrafficSummaryDto';

export interface ITrafficQueryService {
  getAggregates(routeId?: string, windowStartMs?: number): Promise<TrafficSummaryDto[]>;
}

export class TrafficQueryService implements ITrafficQueryService {
  async getAggregates(routeId?: string, windowStartMs?: number): Promise<TrafficSummaryDto[]> {
    const query: any = {};
    if (routeId) query.routeId = routeId;
    if (windowStartMs) query.windowStartMs = windowStartMs;

    const docs = await Aggregate.find(query)
      .sort({ lastAggregatedAtMs: -1 })
      .lean();
    return docs.map(toTrafficSummaryDto);
  }
}
