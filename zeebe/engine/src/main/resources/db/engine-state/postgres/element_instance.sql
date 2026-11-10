-- Element instances of a partition, as stored by RdbmsElementInstanceState.
--
-- Hybrid row: the full ElementInstance is kept as its msgpack payload (the source of truth for
-- reads); the remaining columns are derived from it on every write so they can be queried and
-- indexed. parent_key is -1 for process instances, same as in the RocksDB state.
CREATE TABLE element_instance (
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

CREATE INDEX element_instance_children ON element_instance (partition_id, parent_key, element_instance_key);
