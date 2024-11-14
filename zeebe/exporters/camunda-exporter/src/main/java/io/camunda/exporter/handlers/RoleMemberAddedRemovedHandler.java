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
import io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import java.util.List;
import java.util.Map;

public class RoleMemberAddedRemovedHandler
    implements ExportHandler<RoleMemberEntity, RoleRecordValue> {
  private static final String ADDED_MEMBERS_PARAM = "addedMemberKeys";
  private static final String REMOVED_MEMBERS_PARAM = "removedMemberKeys";
  private static final String SCRIPT =
      """
      if(ctx._source.assignedMemberKeys == null) {
        ctx._source.assignedMemberKeys = params.%1$s;
      }
      else {
        Set result = new HashSet(ctx._source.assignedMemberKeys);
        result.removeAll(params.%2$s);
        result.addAll(params.%1$s);
        ctx._source.assignedMemberKeys = result;
      }
      """
          .formatted(ADDED_MEMBERS_PARAM, REMOVED_MEMBERS_PARAM);

  private final String indexName;

  public RoleMemberAddedRemovedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.ROLE;
  }

  @Override
  public Class<RoleMemberEntity> getEntityType() {
    return RoleMemberEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<RoleRecordValue> record) {
    final Intent intent = record.getIntent();
    return getHandledValueType() == record.getValueType()
        && (intent == RoleIntent.ENTITY_ADDED || intent == RoleIntent.ENTITY_REMOVED);
  }

  @Override
  public List<String> generateIds(final Record<RoleRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public RoleMemberEntity createNewEntity(final String id) {
    return new RoleMemberEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<RoleRecordValue> record, final RoleMemberEntity entity) {
    entity.setRoleKey(record.getKey());
    switch (record.getIntent()) {
      case RoleIntent.ENTITY_ADDED -> entity.addMemberKey(record.getValue().getEntityKey());
      case RoleIntent.ENTITY_REMOVED -> entity.removeMemberKey(record.getValue().getEntityKey());
      default ->
          throw new IllegalArgumentException("unhandled intent: %s".formatted(record.getIntent()));
    }
  }

  @Override
  public void flush(final RoleMemberEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.updateWithScript(
        indexName,
        entity.getId(),
        SCRIPT,
        Map.of(
            ADDED_MEMBERS_PARAM,
            entity.getAddedMemberKeys(),
            REMOVED_MEMBERS_PARAM,
            entity.getRemovedMemberKeys()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
