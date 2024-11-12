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
import io.camunda.webapps.schema.entities.usermanagement.TenantEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class TenantEntityAddedHandler implements ExportHandler<TenantEntity, TenantRecordValue> {
  private final String indexName;

  public TenantEntityAddedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.TENANT;
  }

  @Override
  public Class<TenantEntity> getEntityType() {
    return TenantEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<TenantRecordValue> record) {
    return record.getIntent() == TenantIntent.ENTITY_ADDED
        && record.getValueType() == ValueType.TENANT;
  }

  @Override
  public List<String> generateIds(final Record<TenantRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public TenantEntity createNewEntity(final String id) {
    return new TenantEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<TenantRecordValue> record, final TenantEntity entity) {
    if (entity.getAssignedMemberKeys() == null) {
      entity.setAssignedMemberKeys(new HashSet<>());
    }
    entity.getAssignedMemberKeys().add(record.getValue().getEntityKey());
  }

  @Override
  public void flush(final TenantEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    // the script to add a single value to the assignedMemberKeys field
    final String script =
        "if (ctx._source.assignedMemberKeys == null) "
            + "{ ctx._source.assignedMemberKeys = [params.newKey]; } "
            + "else if (!ctx._source.assignedMemberKeys.contains(params.newKey)) "
            + "{ ctx._source.assignedMemberKeys.add(params.newKey); }";

    final Map<String, Object> params =
        Map.of("newKey", entity.getAssignedMemberKeys().iterator().next());

    batchRequest.updateWithScript(indexName, entity.getId(), script, params);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
