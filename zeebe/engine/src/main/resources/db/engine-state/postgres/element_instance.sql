-- Element instance state of a partition, as stored by RdbmsElementInstanceState.
--
-- Every statement is idempotent, so the script can run on each start.

-- Element instances.
--
-- Hybrid row: the full ElementInstance is kept as its msgpack payload (the source of truth for
-- reads). The remaining columns are derived from it on every write so they can be queried and
-- indexed. parent_key is -1 for process instances, same as in the RocksDB state.
CREATE TABLE IF NOT EXISTS element_instance (
  partition_id INT NOT NULL,
  element_instance_key BIGINT NOT NULL,
  parent_key BIGINT NOT NULL,
  process_instance_key BIGINT NOT NULL,
  process_definition_key BIGINT NOT NULL,
  bpmn_element_type SMALLINT NOT NULL,
  state SMALLINT NOT NULL,
  payload BYTEA NOT NULL,
  PRIMARY KEY (partition_id, element_instance_key)
);

CREATE INDEX IF NOT EXISTS element_instance_children
  ON element_instance (partition_id, parent_key, element_instance_key);

-- Process instances by definition key, and the active process instance count. Only the rows of
-- process instances: 1 is the ordinal of BpmnElementType.PROCESS, which the queries inline.
CREATE INDEX IF NOT EXISTS element_instance_process_by_definition
  ON element_instance (partition_id, process_definition_key, element_instance_key)
  WHERE bpmn_element_type = 1;

-- How often each sequence flow to a joining gateway was taken in a flow scope.
CREATE TABLE IF NOT EXISTS taken_sequence_flow (
  partition_id INT NOT NULL,
  flow_scope_key BIGINT NOT NULL,
  gateway_element_id TEXT NOT NULL,
  sequence_flow_element_id TEXT NOT NULL,
  times_taken INT NOT NULL,
  PRIMARY KEY (partition_id, flow_scope_key, gateway_element_id, sequence_flow_element_id)
);

-- The request awaiting the result of a process instance, with the msgpack
-- AwaitProcessInstanceResultMetadata.
CREATE TABLE IF NOT EXISTS await_process_instance_result (
  partition_id INT NOT NULL,
  process_instance_key BIGINT NOT NULL,
  payload BYTEA NOT NULL,
  PRIMARY KEY (partition_id, process_instance_key)
);

-- Runtime instructions of a process instance, with the msgpack RuntimeInstructions.
CREATE TABLE IF NOT EXISTS runtime_instructions (
  partition_id INT NOT NULL,
  process_instance_key BIGINT NOT NULL,
  payload BYTEA NOT NULL,
  PRIMARY KEY (partition_id, process_instance_key)
);

-- Active process instances by business id.
CREATE TABLE IF NOT EXISTS process_instance_by_business_id (
  partition_id INT NOT NULL,
  business_id TEXT NOT NULL,
  process_definition_id TEXT NOT NULL,
  tenant_id TEXT NOT NULL,
  process_instance_key BIGINT NOT NULL,
  PRIMARY KEY (partition_id, business_id, process_definition_id, tenant_id, process_instance_key)
);
