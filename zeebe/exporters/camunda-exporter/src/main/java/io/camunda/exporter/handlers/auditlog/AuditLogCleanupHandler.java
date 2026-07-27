/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.entities.auditlog.AuditLogCleanupEntity;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.util.VisibleForTesting;

public class AuditLogCleanupHandler<R extends RecordValue>
    extends AbstractAuditLogHandler<AuditLogCleanupEntity, R> {

  public AuditLogCleanupHandler(
      final String indexName,
      final AuditLogTransformer<R> transformer,
      final AuditLogConfiguration configuration) {
    super(indexName, transformer, configuration);
  }

  @Override
  public Class<AuditLogCleanupEntity> getEntityType() {
    return AuditLogCleanupEntity.class;
  }

  @Override
  public AuditLogCleanupEntity createNewEntity(final String id) {
    return new AuditLogCleanupEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<R> record, final AuditLogCleanupEntity entity) {
    final var log = transformer.create(record);
    entity
        .setKey(log.getEntityKey())
        .setKeyField(AuditLogTemplate.ENTITY_KEY)
        .setEntityType(mapEntityType(log.getEntityType()))
        .setPartitionId(record.getPartitionId());
  }

  @Override
  public boolean handlesRecord(final Record<R> record) {
    if (super.handlesRecord(record) && transformer.triggersCleanUp(record)) {
      // archiving of decision instances is done by the StandaloneDecisionArchiverJob
      final AuditLogEntityType auditLogEntityType = AuditLogEntry.getEntityType(record);
      return auditLogEntityType != AuditLogEntityType.DECISION;
    }
    return false;
  }

  @VisibleForTesting
  public AuditLogTransformer<?> getTransformer() {
    return transformer;
  }
}
