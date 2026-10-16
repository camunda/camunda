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
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bumps {@code lastModifiedAt} on the group document when its membership changes. Only that field
 * is written: the group document is never re-indexed from a membership record, which carries no
 * name or description.
 */
public class GroupMembershipChangedHandler
    implements MainIndexExporterHandler<GroupEntity, GroupRecordValue> {

  private static final Set<GroupIntent> SUPPORTED_INTENTS =
      Set.of(GroupIntent.ENTITY_ADDED, GroupIntent.ENTITY_REMOVED, GroupIntent.ENTITIES_SET);
  private final String indexName;

  public GroupMembershipChangedHandler(final String indexName) {
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
    final OffsetDateTime timestamp = toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
    entity.setLastModifiedAt(timestamp);
  }

  @Override
  public void flush(
      final TargetIndex index, final GroupEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(GroupIndex.LAST_MODIFIED_AT, entity.getLastModifiedAt());
    batchRequest.update(index, entity.getId(), updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
