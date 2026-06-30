-- Dataset / report definitions (owned by the webapp).
CREATE TABLE IF NOT EXISTS analytics_dataset (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  name           VARCHAR(255) NOT NULL,
  fact_type      VARCHAR(128) NOT NULL,
  dimensions     VARCHAR(512) NOT NULL,
  window_size_ms BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS analytics_report (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(255) NOT NULL,
  dataset_id      BIGINT       NOT NULL,
  viz_type        VARCHAR(64)  NOT NULL,
  bpmn_process_id VARCHAR(255),
  from_window     BIGINT,
  to_window       BIGINT
);

-- The windowed aggregate the pipeline writes. Created here too (IF NOT EXISTS) so the webapp runs
-- standalone before the pipeline has provisioned it; the pipeline owns the writes.
CREATE TABLE IF NOT EXISTS proc_inst_exec_time_window (
  process_definition_key BIGINT       NOT NULL,
  bpmn_process_id        VARCHAR(255) NOT NULL,
  version                INT          NOT NULL,
  tenant_id              VARCHAR(255) NOT NULL,
  window_start           BIGINT       NOT NULL,
  window_size_ms         BIGINT       NOT NULL,
  completed_count        BIGINT       NOT NULL,
  total_duration_ms      BIGINT       NOT NULL,
  min_duration_ms        BIGINT       NOT NULL,
  max_duration_ms        BIGINT       NOT NULL,
  PRIMARY KEY (process_definition_key, version, tenant_id, window_start)
);
