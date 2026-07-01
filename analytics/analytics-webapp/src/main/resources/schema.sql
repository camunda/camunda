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
  region          VARCHAR(255),
  from_window     BIGINT,
  to_window       BIGINT
);

-- The windowed aggregate the pipeline writes. Created here too (IF NOT EXISTS) so the webapp runs
-- standalone before the pipeline has provisioned it; the pipeline owns the writes.
CREATE TABLE IF NOT EXISTS proc_inst_exec_time_window (
  dataset_id             BIGINT       NOT NULL,
  region                 VARCHAR(255) NOT NULL,
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
  PRIMARY KEY (dataset_id, region, process_definition_key, version, tenant_id, window_start)
);

-- Element heatmap: execution count + execution time per BPMN element, written by the pipeline.
CREATE TABLE IF NOT EXISTS element_execution_window (
  bpmn_process_id        VARCHAR(255) NOT NULL,
  process_definition_key BIGINT       NOT NULL,
  version                INT          NOT NULL,
  tenant_id              VARCHAR(255) NOT NULL,
  element_id             VARCHAR(255) NOT NULL,
  element_type           VARCHAR(64)  NOT NULL,
  window_start           BIGINT       NOT NULL,
  window_size_ms         BIGINT       NOT NULL,
  executed_count         BIGINT       NOT NULL,
  total_duration_ms      BIGINT       NOT NULL,
  min_duration_ms        BIGINT       NOT NULL,
  max_duration_ms        BIGINT       NOT NULL,
  PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, element_id, window_start)
);

-- Process-instance duration percentiles (p50/p75/p90/p99) by definition, written by the pipeline.
CREATE TABLE IF NOT EXISTS proc_inst_duration_pctl_window (
  bpmn_process_id        VARCHAR(255) NOT NULL,
  process_definition_key BIGINT       NOT NULL,
  version                INT          NOT NULL,
  tenant_id              VARCHAR(255) NOT NULL,
  granularity            VARCHAR(16)  NOT NULL,
  window_start           BIGINT       NOT NULL,
  window_size_ms         BIGINT       NOT NULL,
  observation_count      BIGINT       NOT NULL,
  min_duration_ms        BIGINT       NOT NULL,
  max_duration_ms        BIGINT       NOT NULL,
  p50_duration_ms        BIGINT       NOT NULL,
  p75_duration_ms        BIGINT       NOT NULL,
  p90_duration_ms        BIGINT       NOT NULL,
  p99_duration_ms        BIGINT       NOT NULL,
  duration_sketch        BLOB,
  PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, granularity, window_start)
);

-- Per-element duration percentiles, written by the pipeline (complements element_execution_window).
CREATE TABLE IF NOT EXISTS element_duration_pctl_window (
  bpmn_process_id        VARCHAR(255) NOT NULL,
  process_definition_key BIGINT       NOT NULL,
  version                INT          NOT NULL,
  tenant_id              VARCHAR(255) NOT NULL,
  element_id             VARCHAR(255) NOT NULL,
  element_type           VARCHAR(64)  NOT NULL,
  granularity            VARCHAR(16)  NOT NULL,
  window_start           BIGINT       NOT NULL,
  window_size_ms         BIGINT       NOT NULL,
  observation_count      BIGINT       NOT NULL,
  min_duration_ms        BIGINT       NOT NULL,
  max_duration_ms        BIGINT       NOT NULL,
  p50_duration_ms        BIGINT       NOT NULL,
  p75_duration_ms        BIGINT       NOT NULL,
  p90_duration_ms        BIGINT       NOT NULL,
  p99_duration_ms        BIGINT       NOT NULL,
  duration_sketch        BLOB,
  PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, element_id, granularity,
               window_start)
);

-- Percentage/ratio metrics by definition (metric = sla_met | no_incident), written by the pipeline.
CREATE TABLE IF NOT EXISTS proc_ratio_window (
  bpmn_process_id        VARCHAR(255) NOT NULL,
  process_definition_key BIGINT       NOT NULL,
  version                INT          NOT NULL,
  tenant_id              VARCHAR(255) NOT NULL,
  window_start           BIGINT       NOT NULL,
  window_size_ms         BIGINT       NOT NULL,
  metric                 VARCHAR(64)  NOT NULL,
  matched_count          BIGINT       NOT NULL,
  total_count            BIGINT       NOT NULL,
  ratio                  DOUBLE       NOT NULL,
  PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start, metric)
);

-- Forward-looking SLA-met cohort, keyed by the instances' START window, written by the pipeline:
-- started_count is the denominator (instances that started in the window), met_count those that
-- completed within sla_ms. A cohort is final once window_start + window_size_ms + sla_ms elapses.
CREATE TABLE IF NOT EXISTS sla_cohort_window (
  bpmn_process_id        VARCHAR(255) NOT NULL,
  process_definition_key BIGINT       NOT NULL,
  version                INT          NOT NULL,
  tenant_id              VARCHAR(255) NOT NULL,
  window_start           BIGINT       NOT NULL,
  window_size_ms         BIGINT       NOT NULL,
  sla_ms                 BIGINT       NOT NULL,
  started_count          BIGINT       NOT NULL,
  met_count              BIGINT       NOT NULL,
  settled_count          BIGINT       NOT NULL,
  PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start)
);

-- Completion-time distribution per start cohort: of the instances started in a window, how many
-- finished in each duration band (≤10s/≤30s/≤60s/≤120s/>120s). Written by the pipeline.
CREATE TABLE IF NOT EXISTS duration_bucket_window (
  bpmn_process_id        VARCHAR(255) NOT NULL,
  process_definition_key BIGINT       NOT NULL,
  version                INT          NOT NULL,
  tenant_id              VARCHAR(255) NOT NULL,
  window_start           BIGINT       NOT NULL,
  window_size_ms         BIGINT       NOT NULL,
  started_count          BIGINT       NOT NULL,
  le10s                  BIGINT       NOT NULL,
  le30s                  BIGINT       NOT NULL,
  le60s                  BIGINT       NOT NULL,
  le120s                 BIGINT       NOT NULL,
  gt120s                 BIGINT       NOT NULL,
  PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start)
);

-- Distinct-process count (HLL estimate) per tenant+granularity, written by the pipeline. The
-- granularity column carries the time-hierarchy tier (1h finest, 1d coarse) so a range read merges
-- the coarsest tier that still resolves the range.
CREATE TABLE IF NOT EXISTS proc_distinct_window (
  tenant_id         VARCHAR(255) NOT NULL,
  granularity       VARCHAR(16)  NOT NULL,
  window_start      BIGINT       NOT NULL,
  window_size_ms    BIGINT       NOT NULL,
  distinct_estimate BIGINT       NOT NULL,
  distinct_lower    BIGINT       NOT NULL,
  distinct_upper    BIGINT       NOT NULL,
  PRIMARY KEY (tenant_id, granularity, window_start)
);

-- Active instances gauge (in-flight = started − completed) per definition, written by the pipeline.
CREATE TABLE IF NOT EXISTS active_instances (
  bpmn_process_id        VARCHAR(255) NOT NULL,
  process_definition_key BIGINT       NOT NULL,
  version                INT          NOT NULL,
  tenant_id              VARCHAR(255) NOT NULL,
  active_count           BIGINT       NOT NULL,
  PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id)
);

-- Activated (started) instances per window+definition, written by the pipeline (additive count).
CREATE TABLE IF NOT EXISTS activated_instances_window (
  bpmn_process_id        VARCHAR(255) NOT NULL,
  process_definition_key BIGINT       NOT NULL,
  version                INT          NOT NULL,
  tenant_id              VARCHAR(255) NOT NULL,
  window_start           BIGINT       NOT NULL,
  window_size_ms         BIGINT       NOT NULL,
  activated_count        BIGINT       NOT NULL,
  PRIMARY KEY (bpmn_process_id, process_definition_key, version, tenant_id, window_start)
);

-- Incidents raised per flow node per window (the +1 create events), written by the pipeline —
-- counts incidents, not instances, so multiple incidents on one instance all count.
CREATE TABLE IF NOT EXISTS incident_frequency_window (
  bpmn_process_id VARCHAR(255) NOT NULL,
  element_id      VARCHAR(255) NOT NULL,
  tenant_id       VARCHAR(255) NOT NULL,
  window_start    BIGINT       NOT NULL,
  window_size_ms  BIGINT       NOT NULL,
  incident_count  BIGINT       NOT NULL,
  PRIMARY KEY (bpmn_process_id, element_id, tenant_id, window_start)
);

-- Currently-open incidents per flow node (created − resolved gauge), written by the pipeline.
CREATE TABLE IF NOT EXISTS open_incidents (
  bpmn_process_id VARCHAR(255) NOT NULL,
  element_id      VARCHAR(255) NOT NULL,
  tenant_id       VARCHAR(255) NOT NULL,
  open_count      BIGINT       NOT NULL,
  PRIMARY KEY (bpmn_process_id, element_id, tenant_id)
);

-- Deployed process definitions (BPMN XML) written by the pipeline, for rendering the diagram.
CREATE TABLE IF NOT EXISTS process_definition (
  process_definition_key BIGINT       NOT NULL,
  bpmn_process_id        VARCHAR(255) NOT NULL,
  version                INT          NOT NULL,
  tenant_id              VARCHAR(255) NOT NULL,
  bpmn_xml               CLOB         NOT NULL,
  PRIMARY KEY (process_definition_key)
);

-- Top processes by volume (frequent-items) per tenant: the mergeable sketch per granularity+window,
-- written by the pipeline. The webapp merges sketches over the range and derives the ranking.
CREATE TABLE IF NOT EXISTS top_processes_sketch (
  tenant_id       VARCHAR(255) NOT NULL,
  granularity     VARCHAR(16)  NOT NULL,
  window_start    BIGINT       NOT NULL,
  window_size_ms  BIGINT       NOT NULL,
  items_sketch    BLOB         NOT NULL,
  PRIMARY KEY (tenant_id, granularity, window_start)
);
