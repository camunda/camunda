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
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableScope;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterVariableCreatedHandler
    implements ExportHandler<ClusterVariableEntity, ClusterVariableRecordValue> {

  private static final ClusterVariableIntent SUPPORTED_INTENT = ClusterVariableIntent.CREATED;
  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterVariableCreatedHandler.class);
  private final String indexName;
  private final int variableSizeThreshold;

  public ClusterVariableCreatedHandler(final String indexName, final int variableSizeThreshold) {
    this.indexName = indexName;
    this.variableSizeThreshold = variableSizeThreshold;
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
        ClusterVariableIndex.generateID(
            recordValue.getName(),
            recordValue.getTenantId(),
            ClusterVariableScope.fromProtocol(recordValue.getScope().toString())));
  }

  @Override
  public ClusterVariableEntity createNewEntity(final String id) {
    return new ClusterVariableEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ClusterVariableRecordValue> record, final ClusterVariableEntity entity) {
    final var recordValue = record.getValue();
    entity
        .setScope(ClusterVariableScope.fromProtocol(recordValue.getScope().name()))
        .setId(
            ClusterVariableIndex.generateID(
                recordValue.getName(),
                recordValue.getTenantId(),
                ClusterVariableScope.fromProtocol(recordValue.getScope().toString())))
        .setName(recordValue.getName());

    if (ClusterVariableScope.TENANT.equals(entity.getScope())) {
      entity.setTenantId(recordValue.getTenantId());
    }

    if (recordValue.getValue().length() > variableSizeThreshold) {
      entity.setValue(recordValue.getValue().substring(0, variableSizeThreshold));
      entity.setFullValue(recordValue.getValue());
      entity.setPreview(true);
    } else {
      entity.setValue(recordValue.getValue());
      entity.setFullValue(null);
      entity.setPreview(false);
    }
  }

  @Override
  public void flush(final ClusterVariableEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
