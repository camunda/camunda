/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.role;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.usermanagement.index.RoleIndex;
import io.camunda.webapps.schema.entities.usermanagement.RoleEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RoleEntityRemovedHandler implements ExportHandler<RoleEntity, RoleRecordValue> {
  private static final Set<Intent> SUPPORTED_INTENTS = Set.of(RoleIntent.ENTITY_REMOVED);
  private final String indexName;

  public RoleEntityRemovedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.ROLE;
  }

  @Override
  public Class<RoleEntity> getEntityType() {
    return RoleEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<RoleRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<RoleRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public RoleEntity createNewEntity(final String id) {
    return new RoleEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<RoleRecordValue> record, final RoleEntity entity) {
    final Set<Long> updatedAssignedMemberKeys = new HashSet<>(entity.getAssignedMemberKeys());
    updatedAssignedMemberKeys.removeIf(member -> member == record.getValue().getEntityKey());
    entity.setAssignedMemberKeys(updatedAssignedMemberKeys);
  }

  @Override
  public void flush(final RoleEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(RoleIndex.ASSIGNEDMEMBERKEYS, entity.getAssignedMemberKeys());

    batchRequest.update(indexName, entity.getId(), updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
