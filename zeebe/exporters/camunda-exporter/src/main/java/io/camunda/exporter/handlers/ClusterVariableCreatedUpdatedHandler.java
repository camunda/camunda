/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex.FULL_VALUE;
import static io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex.IS_PREVIEW;
import static io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex.METADATA;
import static io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex.SCOPE;
import static io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex.VALUE;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.util.ClusterVariableUtil;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity.MetadataEntry;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableKind;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableScope;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClusterVariableCreatedUpdatedHandler
    implements ExportHandler<ClusterVariableEntity, ClusterVariableRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(ClusterVariableIntent.CREATED, ClusterVariableIntent.UPDATED);
  private final String indexName;
  private final int variableSizeThreshold;

  public ClusterVariableCreatedUpdatedHandler(
      final String indexName, final int variableSizeThreshold) {
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
        && SUPPORTED_INTENTS.contains(record.getIntent());
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
    final var recordValue = record.getValue();
    entity
        .setScope(ClusterVariableScope.fromProtocol(recordValue.getScope()))
        .setName(recordValue.getName());

    // kind is immutable after creation — the UPDATED engine record carries the EnumProperty
    // default (JSON) and does NOT reflect the stored kind. Only set it on CREATED.
    if (record.getIntent() == ClusterVariableIntent.CREATED) {
      entity.setKind(ClusterVariableKind.fromProtocol(recordValue.getKind()));
    }

    if (ClusterVariableScope.TENANT.equals(entity.getScope())) {
      entity.setTenantId(recordValue.getTenantId());
    }

    if (recordValue.getValue().length() > variableSizeThreshold) {
      entity.setValue(recordValue.getValue().substring(0, variableSizeThreshold));
      entity.setFullValue(recordValue.getValue());
      entity.setIsPreview(true);
    } else {
      entity.setValue(recordValue.getValue());
      entity.setFullValue(null);
      entity.setIsPreview(false);
    }

    final Map<String, Object> metadata = recordValue.getMetadata();
    entity.setMetadata(
        metadata.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .map(
                e ->
                    new MetadataEntry(
                        e.getKey(),
                        String.valueOf(e.getValue()),
                        e.getValue() instanceof Number n ? n.doubleValue() : null))
            .toList());
  }

  @Override
  public void flush(final ClusterVariableEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    // Use upsert with only mutable fields as updateFields, so UPDATED records never overwrite the
    // immutable kind field in an existing ES/OS document. On CREATED (document absent) the full
    // entity is indexed. This is consistent with how the RDBMS exporter excludes KIND from UPDATE.
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(NAME, entity.getName());
    updateFields.put(VALUE, entity.getValue());
    updateFields.put(FULL_VALUE, entity.getFullValue());
    updateFields.put(IS_PREVIEW, entity.getIsPreview());
    updateFields.put(SCOPE, entity.getScope());
    updateFields.put(TENANT_ID, entity.getTenantId());
    updateFields.put(METADATA, entity.getMetadata());
    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
