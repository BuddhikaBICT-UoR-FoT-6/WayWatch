import * as express from 'express';
import { fetchPointFlow, fetchTomTomForBbox, ProviderPointData } from '../integrations/tomtom';
import { getRouteSamplePointsWithSeverity } from '../models/routesPoints';
const router = express.Router();

// GET /api/v1/traffic?lat=&lon=
router.get('/api/v1/traffic', async (req, res) => {
  const lat = Number(req.query.lat);
  const lon = Number(req.query.lon);
  if (Number.isNaN(lat) || Number.isNaN(lon)) return res.status(400).json({ ok: false, message: 'lat/lon required' });
  try {
    const data = await fetchPointFlow(lat, lon);
    return res.json({ ok: true, data });
  } catch (e: any) {
    console.error('/api/v1/traffic error', e?.message || e);
    return res.status(500).json({ ok: false, error: e?.message || 'Failed to fetch traffic' });
  }
});

// POST /api/v1/traffic/bbox - body: { north, west, south, east, maxPoints? }
router.post('/api/v1/traffic/bbox', async (req, res) => {
  try {
    const { north, west, south, east, maxPoints } = req.body || {};
    if ([north, west, south, east].some(v => typeof v !== 'number')) return res.status(400).json({ ok: false, error: 'invalid bbox' });
    const data = await fetchTomTomForBbox({ north, west, south, east }, { maxPoints: Number(maxPoints || 12) });
    return res.json({ ok: true, data });
  } catch (e: any) {
    console.error('/api/v1/traffic/bbox error', e?.message || e);
    return res.status(500).json({ ok: false, error: e?.message || 'Failed' });
  }
});

// GET /api/v1/routes/:routeId/points?maxPoints=12&windowStartMs=...
router.get('/api/v1/routes/:routeId/points', async (req, res) => {
  try {
    const routeId = String(req.params.routeId || '').trim();
    if (!routeId) return res.status(400).json({ ok: false, error: 'routeId required' });

    const maxPoints = Number(req.query.maxPoints || 12);
    const windowStartMs = req.query.windowStartMs != null ? Number(req.query.windowStartMs) : undefined;

    if (!Number.isFinite(maxPoints) || maxPoints <= 0) {
      return res.status(400).json({ ok: false, error: 'maxPoints must be a positive number' });
    }

    const points = await getRouteSamplePointsWithSeverity(
      routeId,
      Number.isFinite(windowStartMs as any) ? (windowStartMs as any as number) : undefined,
      Math.max(1, Math.min(50, maxPoints)),
    );

    return res.json({ ok: true, data: { routeId, points } });
  } catch (e: any) {
    console.error('/api/v1/routes/:routeId/points error', e?.message || e);
    return res.status(500).json({ ok: false, error: e?.message || 'Failed' });
  }
});

export default router;
