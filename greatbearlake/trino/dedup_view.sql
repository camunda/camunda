-- Trino deduplication view for zeebe_records.
--
-- The parquet exporter guarantees at-least-once delivery, so the same position
-- may appear in more than one Parquet file after a broker restart.
-- This view retains only the first row per (partition_id, position).
--
-- Usage:
--   SELECT * FROM iceberg.zeebe.zeebe_records_deduped
--   WHERE value_type = 'PROCESS_INSTANCE'
--     AND intent     = 'ELEMENT_COMPLETED'

CREATE OR REPLACE VIEW iceberg.zeebe.zeebe_records_deduped AS
SELECT
  position,
  partition_id,
  key,
  record_type,
  value_type,
  intent,
  timestamp,
  broker_version,
  json,
  ingested_at
FROM (
  SELECT
    *,
    ROW_NUMBER() OVER (
      PARTITION BY partition_id, position
      ORDER BY ingested_at
    ) AS rn
  FROM iceberg.zeebe.zeebe_records
)
WHERE rn = 1;
