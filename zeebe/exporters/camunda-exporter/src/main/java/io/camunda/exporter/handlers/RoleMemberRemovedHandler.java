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
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import java.util.List;

public class RoleMemberRemovedHandler implements ExportHandler<RoleMemberEntity, RoleRecordValue> {
  private final String indexName;

  public RoleMemberRemovedHandler(final String indexName) {
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
    return getHandledValueType() == record.getValueType()
        && record.getIntent() == RoleIntent.ENTITY_REMOVED;
  }

  @Override
  public List<String> generateIds(final Record<RoleRecordValue> record) {
    final RoleRecordValue value = record.getValue();
    return List.of(
        RoleIndex.JOIN_RELATION_FACTORY.createChildId(
            value.getRoleId(), value.getEntityId(), value.getEntityType()));
  }

  @Override
  public RoleMemberEntity createNewEntity(final String id) {
    return new RoleMemberEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<RoleRecordValue> record, final RoleMemberEntity entity) {
    final RoleRecordValue value = record.getValue();
    entity
        .setMemberId(value.getEntityId())
        .setJoin(RoleIndex.JOIN_RELATION_FACTORY.createChild(value.getRoleId()));
  }

  @Override
  public void flush(final RoleMemberEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.deleteWithRouting(indexName, entity.getId(), entity.getJoin().parent());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
