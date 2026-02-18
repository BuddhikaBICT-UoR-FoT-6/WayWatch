// File: server/src/models/routesPoints.ts
import mongoose from 'mongoose';

const MAX_POINTS_DEFAULT = 12;

export type RoutePointWithSeverity = { lat: number; lon: number; severity: number };

/**
 * Returns up to maxPoints sample points for a route, each with its sample severity.
 * This is a pragmatic way to render severity markers on the map without route geometry.
 */
export async function getRouteSamplePointsWithSeverity(
  routeId: string,
  windowStartMs?: number,
  maxPoints = MAX_POINTS_DEFAULT,
): Promise<RoutePointWithSeverity[]> {
  const coll = mongoose.connection.collection('samples');

  const match: any = { routeId };
  if (typeof windowStartMs === 'number' && Number.isFinite(windowStartMs)) {
    match.windowStartMs = windowStartMs;
  }

  // Sample random documents to show distribution across route.
  const pipeline: any[] = [
    { $match: match },
    { $sample: { size: Math.max(1, Math.min(50, maxPoints)) } },
    { $project: { _id: 0, lat: 1, lon: 1, severity: 1, point: 1 } },
  ];

  const docs = await coll.aggregate(pipeline).toArray();
  const points: RoutePointWithSeverity[] = [];

  for (const d of docs) {
    if (!d) continue;

    // Prefer explicit lat/lon fields when available
    if (typeof d.lat === 'number' && typeof d.lon === 'number') {
      const sev = typeof d.severity === 'number' ? d.severity : 2;
      points.push({ lat: d.lat, lon: d.lon, severity: sev });
      if (points.length >= maxPoints) break;
      continue;
    }

    // Fallback: some docs may have { point: { lat, lon } }
    if (d.point && typeof d.point.lat === 'number' && typeof d.point.lon === 'number') {
      const sev = typeof d.severity === 'number' ? d.severity : 2;
      points.push({ lat: d.point.lat, lon: d.point.lon, severity: sev });
      if (points.length >= maxPoints) break;
      continue;
    }
  }

  return points.slice(0, maxPoints);
}
