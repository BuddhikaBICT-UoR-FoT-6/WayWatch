import axios from 'axios';

const OVERPASS_URL = process.env.OVERPASS_URL || 'https://overpass-api.de/api/interpreter';
const SRI_LANKA_AREA = 3600065362;

export type NearbyRoute = {
  ref: string;
  name?: string;
  id: number;
  distanceKm?: number;
};

/**
 * Find bus route relations near a point.
 *
 * Implementation notes:
 * - Querying ALL bus relations in Sri Lanka is too slow and often 504s.
 * - Instead, fetch ways around the point, then ask for relations that reference those ways.
 * - This is best-effort; OSM bus-route coverage varies.
 */
export async function listNearbyBusRoutes(lat: number, lon: number, radiusKm = 5, limit = 20): Promise<NearbyRoute[]> {
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return [];

  const radiusM = Math.round(Math.max(300, Math.min(25_000, radiusKm * 1000)));
  const outLimit = Math.max(1, Math.min(50, limit));

  const overpass = `
[out:json][timeout:25];
area(${SRI_LANKA_AREA})->.sl;
(
  way(around:${radiusM},${lat},${lon})(area.sl)->.w;
  relation(bw.w)["type"="route"]["route"="bus"](area.sl);
);
out tags ${outLimit};
`;

  const resp = await axios.post(OVERPASS_URL, overpass, {
    headers: { 'Content-Type': 'text/plain' },
    timeout: 25_000,
  });

  const json = resp.data as { elements?: Array<{ type: string; id: number; tags?: Record<string, string> }> };

  const out: NearbyRoute[] = [];
  for (const el of json.elements || []) {
    if (el.type !== 'relation') continue;
    const ref = el.tags?.ref;
    if (!ref) continue;
    out.push({ ref, name: el.tags?.name, id: el.id });
  }

  // De-dupe by ref, numeric sort
  const dedup = new Map<string, NearbyRoute>();
  for (const r of out) if (!dedup.has(r.ref)) dedup.set(r.ref, r);
  const list = Array.from(dedup.values());

  list.sort((a, b) => {
    const an = Number(a.ref);
    const bn = Number(b.ref);
    if (Number.isFinite(an) && Number.isFinite(bn)) return an - bn;
    return a.ref.localeCompare(b.ref);
  });

  return list.slice(0, outLimit);
}
