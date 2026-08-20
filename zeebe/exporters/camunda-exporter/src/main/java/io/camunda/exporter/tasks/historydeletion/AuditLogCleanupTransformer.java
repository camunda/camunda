/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogCleanupEntity;
import java.util.List;
import java.util.Set;

public final class AuditLogCleanupTransformer {

  public static final String AUDIT_LOG_CLEANUP_ID = "%s-%s";

  private AuditLogCleanupTransformer() {
    // Utility class
  }

  /**
   * Builds the list of {@link AuditLogCleanupEntity} entries for the resources that were
   * successfully deleted. These entries are written to the audit log cleanup index so that the
   * audit log archiver can later purge the corresponding audit log records.
   *
   * @param historyDeletionEntities The history deletion entities that were processed
   * @param deletedHistoryDeletionIds The history-deletion document IDs that were successfully
   *     processed
   * @return The list of cleanup entities to index
   */
  public static List<AuditLogCleanupEntity> buildAuditLogCleanupEntries(
      final List<HistoryDeletionEntity> historyDeletionEntities,
      final Set<String> deletedHistoryDeletionIds) {
    return historyDeletionEntities.stream()
        .filter(entity -> deletedHistoryDeletionIds.contains(entity.getId()))
        .map(AuditLogCleanupTransformer::toAuditLogCleanupEntity)
        .toList();
  }

  /**
   * Converts a {@link HistoryDeletionEntity} to an {@link AuditLogCleanupEntity}.
   *
   * @param entity The history deletion entity to convert
   * @return The audit log cleanup entity
   */
  private static AuditLogCleanupEntity toAuditLogCleanupEntity(final HistoryDeletionEntity entity) {
    final String keyField =
        switch (entity.getResourceType()) {
          case PROCESS_INSTANCE -> AuditLogTemplate.PROCESS_INSTANCE_KEY;
          case PROCESS_DEFINITION -> AuditLogTemplate.PROCESS_DEFINITION_KEY;
          case DECISION_INSTANCE -> AuditLogTemplate.DECISION_EVALUATION_KEY;
          case DECISION_REQUIREMENTS -> AuditLogTemplate.DECISION_REQUIREMENTS_KEY;
        };
    final var id =
        AUDIT_LOG_CLEANUP_ID.formatted(entity.getBatchOperationKey(), entity.getResourceKey());
    // avoid setting entityType so job later deletes only by key
    return new AuditLogCleanupEntity()
        .setId(id)
        .setKey(String.valueOf(entity.getResourceKey()))
        .setKeyField(keyField)
        .setEntityType(null)
        .setPartitionId((int) entity.getPartitionId());
  }
}
