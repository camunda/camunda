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
import io.camunda.webapps.schema.entities.usermanagement.GroupEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class GroupEntityAddedHandler implements ExportHandler<GroupEntity, GroupRecordValue> {

  public static final String SCRIPT_ADD_ENTITY =
      """
      if (ctx._source.assignedMemberKeys == null) {
        ctx._source.assignedMemberKeys = params.addKeys;
      }
      for (newKey in params.addKeys) {
        if (!ctx._source.assignedMemberKeys.contains(newKey)) {
          ctx._source.assignedMemberKeys.add(newKey);
        }
      }""";

  private final String indexName;

  public GroupEntityAddedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.GROUP;
  }

  @Override
  public Class<GroupEntity> getEntityType() {
    return GroupEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<GroupRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && GroupIntent.ENTITY_ADDED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<GroupRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public GroupEntity createNewEntity(final String id) {
    return new GroupEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<GroupRecordValue> record, final GroupEntity entity) {
    final GroupRecordValue value = record.getValue();
    final var entityKey = value.getEntityKey();

    var memberKeys = entity.getAssignedMemberKeys();
    memberKeys = memberKeys == null ? new HashSet<>() : memberKeys;
    memberKeys.add(entityKey);

    entity.setAssignedMemberKeys(memberKeys);
  }

  @Override
  public void flush(final GroupEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {

    batchRequest.updateWithScript(
        indexName,
        entity.getId(),
        SCRIPT_ADD_ENTITY,
        Map.of("addKeys", entity.getAssignedMemberKeys()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
