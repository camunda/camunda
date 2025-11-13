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
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ClusterVariableHandler
    implements ExportHandler<ClusterVariableEntity, ClusterVariableRecordValue> {

  private static final Set<ClusterVariableIntent> SUPPORTED_INTENTS =
      EnumSet.of(ClusterVariableIntent.CREATED, ClusterVariableIntent.DELETED);
  private final String indexName;
  private final int variableSizeThreshold;

  public ClusterVariableHandler(final String indexName, final int variableSizeThreshold) {
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
    return SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<ClusterVariableRecordValue> record) {
    final var recordValue = record.getValue();
    switch (recordValue.getScope()) {
      case GLOBAL -> {
        return List.of(recordValue.getName());
      }
      case TENANT -> {
        return List.of(String.format("%s-%s", recordValue.getName(), recordValue.getTenantId()));
      }
      default ->
          throw new IllegalArgumentException(
              "Cluster variable with unspecified scope can not be exported");
    }
  }

  @Override
  public ClusterVariableEntity createNewEntity(final String id) {
    return new ClusterVariableEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ClusterVariableRecordValue> record, final ClusterVariableEntity entity) {
    final var recordValue = record.getValue();

    switch (recordValue.getScope()) {
      case GLOBAL -> entity.setScope("GLOBAL").setId(recordValue.getName());
      case TENANT ->
          entity
              .setScope("TENANT")
              .setResourceId(recordValue.getTenantId())
              .setId(String.format("%s-%s", recordValue.getName(), recordValue.getTenantId()));
      default ->
          throw new IllegalArgumentException(
              "Cluster variable with unspecified scope can not be exported");
    }

    entity.setName(recordValue.getName());

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
