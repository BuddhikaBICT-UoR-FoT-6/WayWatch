import cron from 'node-cron';
import mongoose from 'mongoose';
import winston from 'winston';

const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info',
  format: winston.format.combine(winston.format.timestamp(), process.env.NODE_ENV === 'production' ? winston.format.json() : winston.format.simple()),
  transports: [new winston.transports.Console()],
});

export function startAggregationCron() {
  if (process.env.NODE_ENV === 'test') return;

  cron.schedule('*/5 * * * *', async () => {
    try {
      logger.info('Running aggregation cron...');
      const Sample = mongoose.model('Sample');
      const Aggregate = mongoose.model('Aggregate');

      // We aggregate data from the last 60 minutes
      const timeThreshold = Date.now() - 60 * 60 * 1000;

      const pipeline = [
        { $match: { reportedAtMs: { $gte: timeThreshold } } },
        { 
          $group: { 
            _id: { routeId: '$routeId', windowStartMs: '$windowStartMs', segmentId: '$segmentId' },
            severities: { $push: '$severity' },
            sampleCount: { $sum: 1 }
          }
        }
      ];

      const results = await Sample.aggregate(pipeline);

      let updatedCount = 0;
      for (const r of results) {
        const severities = r.severities.map(Number).sort((a: number, b: number) => a - b);
        const len = severities.length;
        if (len === 0) continue;

        const avg = severities.reduce((a: number, b: number) => a + b, 0) / len;
        const p = (q: number) => severities[Math.floor(q * (len - 1))];

        const doc = {
          routeId: r._id.routeId,
          windowStartMs: r._id.windowStartMs,
          segmentId: r._id.segmentId,
          severityAvg: avg,
          severityP50: p(0.5),
          severityP90: p(0.9),
          sampleCount: len,
          lastAggregatedAtMs: Date.now()
        };

        await Aggregate.updateOne(
          { routeId: doc.routeId, windowStartMs: doc.windowStartMs, segmentId: doc.segmentId },
          { $set: doc },
          { upsert: true }
        );
        updatedCount++;
      }
      logger.info(`Aggregation cron completed, processed ${updatedCount} windows.`);
    } catch (e) {
      logger.error('Aggregation cron failed', { err: e });
    }
  });
}
