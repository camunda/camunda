/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.TenantDbModel;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.TenantWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantExportHandler implements RdbmsExportHandler<TenantRecordValue> {

  private static final Logger LOG = LoggerFactory.getLogger(TenantExportHandler.class);

  private static final Set<TenantIntent> EXPORTABLE_INTENTS =
      Set.of(
          TenantIntent.CREATED,
          TenantIntent.UPDATED,
          TenantIntent.DELETED,
          TenantIntent.ENTITY_ADDED,
          TenantIntent.ENTITY_REMOVED);

  private final TenantWriter tenantWriter;
  private final HistoryCleanupService historyCleanupService;

  public TenantExportHandler(
      final TenantWriter tenantWriter, final HistoryCleanupService historyCleanupService) {
    this.tenantWriter = tenantWriter;
    this.historyCleanupService = historyCleanupService;
  }

  @Override
  public boolean canExport(final Record<TenantRecordValue> record) {
    return record.getIntent() != null
        && record.getIntent() instanceof final TenantIntent intent
        && EXPORTABLE_INTENTS.contains(intent);
  }

  @Override
  public void export(final Record<TenantRecordValue> record) {
    final TenantRecordValue value = record.getValue();
    switch (record.getIntent()) {
      case TenantIntent.CREATED -> tenantWriter.create(map(value));
      case TenantIntent.UPDATED -> tenantWriter.update(map(value));
      case TenantIntent.DELETED -> {
        tenantWriter.delete(map(value));
        final var endDate = DateUtil.toOffsetDateTime(record.getTimestamp());
        historyCleanupService.scheduleAuditLogsForHistoryCleanup(value.getTenantId(), endDate);
      }
      case TenantIntent.ENTITY_ADDED ->
          tenantWriter.addMember(
              new TenantMemberDbModel.Builder()
                  .tenantId(value.getTenantId())
                  .entityId(value.getEntityId())
                  .entityType(value.getEntityType().name())
                  .build());
      case TenantIntent.ENTITY_REMOVED ->
          tenantWriter.removeMember(
              new TenantMemberDbModel.Builder()
                  .tenantId(value.getTenantId())
                  .entityId(value.getEntityId())
                  .entityType(value.getEntityType().name())
                  .build());
      default -> LOG.warn("Unexpected intent {} for tenant record", record.getIntent());
    }
  }

  private TenantDbModel map(final TenantRecordValue recordValue) {
    return new TenantDbModel.Builder()
        .tenantKey(recordValue.getTenantKey())
        .tenantId(recordValue.getTenantId())
        .name(recordValue.getName())
        .description(recordValue.getDescription())
        .build();
  }
}
