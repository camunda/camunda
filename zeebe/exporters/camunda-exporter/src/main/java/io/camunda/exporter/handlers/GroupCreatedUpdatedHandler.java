/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.toOffsetDateTime;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.entities.usermanagement.GroupEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GroupCreatedUpdatedHandler
    implements MainIndexExporterHandler<GroupEntity, GroupRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(GroupIntent.CREATED, GroupIntent.UPDATED);
  private final String indexName;

  public GroupCreatedUpdatedHandler(final String indexName) {
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
        && SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<GroupRecordValue> record) {
    return List.of(GroupIndex.JOIN_RELATION_FACTORY.createParentId(record.getValue().getGroupId()));
  }

  @Override
  public GroupEntity createNewEntity(final String id) {
    return new GroupEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<GroupRecordValue> record, final GroupEntity entity) {
    final GroupRecordValue value = record.getValue();
    final var joinRelation = GroupIndex.JOIN_RELATION_FACTORY.createParent();
    final OffsetDateTime timestamp = toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
    entity
        .setKey(value.getGroupKey())
        .setGroupId(value.getGroupId())
        .setName(value.getName())
        .setDescription(value.getDescription())
        .setExternalId(value.getExternalId())
        .setAttributes(AttributesJson.serialize(value.getAttributes()))
        .setLastModifiedAt(timestamp)
        .setJoin(joinRelation);
    if (record.getIntent() == GroupIntent.CREATED) {
      entity.setCreatedAt(timestamp);
    }
  }

  @Override
  public void flush(
      final TargetIndex index, final GroupEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    // createdAt is deliberately left out of the update fields: it is only written when the
    // document is inserted, so that an UPDATED record never overwrites the original creation time
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(GroupIndex.KEY, entity.getKey());
    updateFields.put(GroupIndex.GROUP_ID, entity.getGroupId());
    updateFields.put(GroupIndex.NAME, entity.getName());
    updateFields.put(GroupIndex.DESCRIPTION, entity.getDescription());
    updateFields.put(GroupIndex.EXTERNAL_ID, entity.getExternalId());
    updateFields.put(GroupIndex.ATTRIBUTES, entity.getAttributes());
    updateFields.put(GroupIndex.LAST_MODIFIED_AT, entity.getLastModifiedAt());
    batchRequest.upsert(index, entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
