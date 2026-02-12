/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.service.AuditLogWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogTenant;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.util.VisibleForTesting;

public class AuditLogExportHandler<R extends RecordValue> implements RdbmsExportHandler<R> {
  private final AuditLogWriter auditLogWriter;
  private final AuditLogTransformer<R> transformer;
  private final AuditLogConfiguration configuration;

  public AuditLogExportHandler(
      final AuditLogWriter auditLogWriter,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final AuditLogTransformer<R> transformer,
      final AuditLogConfiguration configuration) {
    this.auditLogWriter = auditLogWriter;
    this.transformer = transformer;
    this.configuration = configuration;
  }

  @VisibleForTesting
  public AuditLogTransformer<R> getTransformer() {
    return transformer;
  }

  @VisibleForTesting
  static String generateAuditLogKey(final Record<?> record) {
    return record.getPartitionId() + "-" + record.getPosition();
  }

  @Override
  public boolean canExport(final Record<R> record) {
    final var info = AuditLogInfo.of(record);

    return transformer.supports(record) && configuration.isEnabled(info);
  }

  @Override
  public void export(final Record<R> record) {
    auditLogWriter.create(map(record));
  }

  private AuditLogDbModel map(final Record<R> record) {
    final AuditLogEntry log = transformer.create(record);

    return toAuditLogModel(log, record);
  }

  private AuditLogDbModel toAuditLogModel(final AuditLogEntry log, final Record<R> record) {
    final var key = generateAuditLogKey(record);

    final var auditLog =
        new AuditLogDbModel.Builder()
            .auditLogKey(key)
            // Generic fields
            .entityKey(log.getEntityKey())
            .entityType(log.getEntityType())
            .entityDescription(log.getEntityDescription())
            .category(log.getCategory())
            .operationType(log.getOperationType())
            .actorId(log.getActor().actorId())
            .actorType(log.getActor().actorType())
            .agentElementId(log.getAgent().map(Agent::getElementId).orElse(null))
            .tenantId(log.getTenant().map(AuditLogTenant::tenantId).orElse(null))
            .tenantScope(
                log.getTenant().map(AuditLogTenant::scope).orElse(AuditLogTenantScope.GLOBAL))
            .batchOperationKey(log.getBatchOperationKey())
            .batchOperationType(mapBatchOperationType(log.getBatchOperationType()))
            .processInstanceKey(log.getProcessInstanceKey())
            .rootProcessInstanceKey(log.getRootProcessInstanceKey())
            .entityVersion(log.getEntityVersion())
            .entityValueType(log.getEntityValueType())
            .entityOperationIntent(log.getEntityOperationIntent())
            .timestamp(log.getTimestamp())
            .annotation(log.getAnnotation())
            // Transformer specific fields
            .result(log.getResult())
            .processDefinitionId(log.getProcessDefinitionId())
            .processDefinitionKey(log.getProcessDefinitionKey())
            .elementInstanceKey(log.getElementInstanceKey())
            .jobKey(log.getJobKey())
            .userTaskKey(log.getUserTaskKey())
            .decisionEvaluationKey(log.getDecisionEvaluationKey())
            .decisionRequirementsId(log.getDecisionRequirementsId())
            .decisionRequirementsKey(log.getDecisionRequirementsKey())
            .decisionDefinitionId(log.getDecisionDefinitionId())
            .decisionDefinitionKey(log.getDecisionDefinitionKey())
            .deploymentKey(log.getDeploymentKey())
            .formKey(log.getFormKey())
            .resourceKey(log.getResourceKey())
            .relatedEntityKey(log.getRelatedEntityKey())
            .relatedEntityType(log.getRelatedEntityType())
            .partitionId(record.getPartitionId());

    return auditLog.build();
  }

  private io.camunda.search.entities.BatchOperationType mapBatchOperationType(
      final io.camunda.zeebe.protocol.record.value.BatchOperationType batchOperationType) {
    if (batchOperationType == null) {
      return null;
    }
    return io.camunda.search.entities.BatchOperationType.valueOf(batchOperationType.name());
  }
}
