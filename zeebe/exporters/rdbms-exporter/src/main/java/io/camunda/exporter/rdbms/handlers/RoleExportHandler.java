/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.RoleDbModel;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.db.rdbms.write.service.RoleWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleExportHandler implements RdbmsExportHandler<RoleRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(RoleExportHandler.class);

  private static final Set<RoleIntent> EXPORTABLE_INTENTS =
      Set.of(
          RoleIntent.CREATED,
          RoleIntent.UPDATED,
          RoleIntent.DELETED,
          RoleIntent.ENTITY_ADDED,
          RoleIntent.ENTITY_REMOVED);

  private final RoleWriter roleWriter;

  public RoleExportHandler(final RoleWriter roleWriter) {
    this.roleWriter = roleWriter;
  }

  @Override
  public boolean canExport(final Record<RoleRecordValue> record) {
    return record.getIntent() != null
        && record.getIntent() instanceof final RoleIntent intent
        && EXPORTABLE_INTENTS.contains(intent);
  }

  @Override
  public void export(final Record<RoleRecordValue> record) {
    final RoleRecordValue value = record.getValue();
    switch (record.getIntent()) {
      case RoleIntent.CREATED -> roleWriter.create(map(value));
      case RoleIntent.UPDATED -> roleWriter.update(map(value));
      case RoleIntent.DELETED -> roleWriter.delete(value.getRoleKey());
      case RoleIntent.ENTITY_ADDED ->
          roleWriter.addMember(
              new RoleMemberDbModel.Builder()
                  .roleKey(value.getRoleKey())
                  .entityId(value.getEntityId())
                  .entityType(value.getEntityType().name())
                  .build());
      case RoleIntent.ENTITY_REMOVED ->
          roleWriter.removeMember(
              new RoleMemberDbModel.Builder()
                  .roleKey(value.getRoleKey())
                  .entityId(value.getEntityId())
                  .entityType(value.getEntityType().name())
                  .build());
      default -> LOG.warn("Unexpected intent {} for role record", record.getIntent());
    }
  }

  private RoleDbModel map(final RoleRecordValue decision) {
    return new RoleDbModel.Builder()
        .roleKey(decision.getRoleKey())
        .name(decision.getName())
        .build();
  }
}
