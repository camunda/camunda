/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.util.ClusterVariableUtil;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import java.util.List;

public class ClusterVariableDeletedHandler
    implements ExportHandler<ClusterVariableEntity, ClusterVariableRecordValue> {

  private static final ClusterVariableIntent SUPPORTED_INTENT = ClusterVariableIntent.DELETED;
  private final String indexName;

  public ClusterVariableDeletedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.CLUSTER_VARIABLE;
  }

  @Override
  public Class<ClusterVariableEntity> getEntityType() {
    return ClusterVariableEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ClusterVariableRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && SUPPORTED_INTENT.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<ClusterVariableRecordValue> record) {
    final var recordValue = record.getValue();
    return List.of(
        ClusterVariableUtil.generateID(
            recordValue.getName(),
            recordValue.getTenantId(),
            io.camunda.search.entities.ClusterVariableScope.valueOf(
                recordValue.getScope().name())));
  }

  @Override
  public ClusterVariableEntity createNewEntity(final String id) {
    return new ClusterVariableEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ClusterVariableRecordValue> record, final ClusterVariableEntity entity) {
    // no-op since the entity will be deleted
  }

  @Override
  public void flush(final ClusterVariableEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.delete(indexName, entity.getId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
