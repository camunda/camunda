-- Dataset / report definitions (owned by the webapp). All dashboard/report metrics are served from
-- the neutral serving store (see AnalyticsServingConfig), not from bespoke tables here.
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
  region          VARCHAR(255),
  from_window     BIGINT,
  to_window       BIGINT
);
