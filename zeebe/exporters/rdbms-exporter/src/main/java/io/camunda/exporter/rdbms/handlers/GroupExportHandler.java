/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.db.rdbms.write.service.GroupWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
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
          GroupIntent.ENTITY_REMOVED);

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
    switch (record.getIntent()) {
      case GroupIntent.CREATED -> groupWriter.create(map(value));
      case GroupIntent.UPDATED -> groupWriter.update(map(value));
      case GroupIntent.DELETED -> groupWriter.delete(value.getGroupKey());
      case GroupIntent.ENTITY_ADDED ->
          groupWriter.addMember(
              new GroupMemberDbModel.Builder()
                  .groupKey(value.getGroupKey())
                  // TODO: revisit with https://github.com/camunda/camunda/pull/30697
                  .entityKey(Long.parseLong(value.getEntityId()))
                  .entityType(value.getEntityType().name())
                  .build());
      case GroupIntent.ENTITY_REMOVED ->
          groupWriter.removeMember(
              new GroupMemberDbModel.Builder()
                  .groupKey(value.getGroupKey())
                  // TODO: revisit https://github.com/camunda/camunda/pull/30697
                  .entityKey(Long.parseLong(value.getEntityId()))
                  .entityType(value.getEntityType().name())
                  .build());
      default -> LOG.warn("Unexpected intent {} for group record", record.getIntent());
    }
  }

  private GroupDbModel map(final GroupRecordValue decision) {
    return new GroupDbModel.Builder()
        .groupKey(decision.getGroupKey())
        .name(decision.getName())
        .build();
  }
}
