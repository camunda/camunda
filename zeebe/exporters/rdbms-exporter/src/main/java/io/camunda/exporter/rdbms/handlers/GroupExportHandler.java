/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.exporter.rdbms.utils.DateUtil.toOffsetDateTime;

import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.db.rdbms.write.service.GroupWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import java.time.OffsetDateTime;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupExportHandler implements RdbmsExportHandler<GroupRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(GroupExportHandler.class);

  private static final Set<GroupIntent> EXPORTABLE_INTENTS =
      Set.of(
          GroupIntent.CREATED,
          GroupIntent.UPDATED,
          GroupIntent.DELETED,
          GroupIntent.ENTITY_ADDED,
          GroupIntent.ENTITY_REMOVED,
          GroupIntent.ENTITIES_SET);

  private final GroupWriter groupWriter;

  public GroupExportHandler(final GroupWriter groupWriter) {
    this.groupWriter = groupWriter;
  }

  @Override
  public boolean canExport(final Record<GroupRecordValue> record) {
    return record.getIntent() != null
        && record.getIntent() instanceof final GroupIntent intent
        && EXPORTABLE_INTENTS.contains(intent);
  }

  @Override
  public void export(final Record<GroupRecordValue> record) {
    final GroupRecordValue value = record.getValue();
    final OffsetDateTime timestamp = toOffsetDateTime(record.getTimestamp());
    switch (record.getIntent()) {
      // createdAt is only set on CREATED: UPDATE does not write CREATED_AT, so passing null is safe
      case GroupIntent.CREATED -> groupWriter.create(map(value, timestamp, timestamp));
      case GroupIntent.UPDATED -> groupWriter.update(map(value, null, timestamp));
      case GroupIntent.DELETED -> groupWriter.delete(value.getGroupId());
      case GroupIntent.ENTITY_ADDED -> {
        groupWriter.addMember(
            new GroupMemberDbModel.Builder()
                .groupId(value.getGroupId())
                .entityId(value.getEntityId())
                .entityType(value.getEntityType().name())
                .build());
        groupWriter.updateLastModifiedAt(value.getGroupId(), timestamp);
      }
      case GroupIntent.ENTITY_REMOVED -> {
        groupWriter.removeMember(
            new GroupMemberDbModel.Builder()
                .groupId(value.getGroupId())
                .entityId((value.getEntityId()))
                .entityType(value.getEntityType().name())
                .build());
        groupWriter.updateLastModifiedAt(value.getGroupId(), timestamp);
      }
      // the membership changes themselves arrive as ENTITY_ADDED / ENTITY_REMOVED, so only the
      // timestamp is bumped here
      case GroupIntent.ENTITIES_SET ->
          groupWriter.updateLastModifiedAt(value.getGroupId(), timestamp);
      default -> LOG.warn("Unexpected intent {} for group record", record.getIntent());
    }
  }

  private GroupDbModel map(
      final GroupRecordValue decision,
      final OffsetDateTime createdAt,
      final OffsetDateTime lastModifiedAt) {
    return new GroupDbModel.Builder()
        .groupId(decision.getGroupId())
        .groupKey(decision.getGroupKey())
        .name(decision.getName())
        .description(decision.getDescription())
        .externalId(decision.getExternalId())
        .source(emptyToNull(decision.getSource()))
        .attributes(AttributesJson.serialize(decision.getAttributes()))
        .createdAt(createdAt)
        .lastModifiedAt(lastModifiedAt)
        .build();
  }

  private static String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
