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
import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogTenant;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A generic handler for audit log records that delegates record-type-specific transformation to an
 * {@link AuditLogTransformer}.
 *
 * <p>This handler provides common audit log functionality such as setting actor data, batch
 * operation information, and handling both successful operations and rejections.
 *
 * <p>To add audit logging for a new record type:
 *
 * <ol>
 *   <li>Create a transformer implementing {@link AuditLogTransformer}
 *   <li>Instantiate an {@link AuditLogHandler} with the transformer
 *   <li>Register the handler in the exporter resource provider
 * </ol>
 *
 * @param <R> the record value type this handler processes
 */
public class AuditLogHandler<R extends RecordValue> implements ExportHandler<AuditLogEntity, R> {

  private final String indexName;
  private final AuditLogTransformer<R> transformer;
  private final AuditLogConfiguration configuration;

  public AuditLogHandler(
      final String indexName,
      final AuditLogTransformer<R> transformer,
      final AuditLogConfiguration configuration) {
    this.indexName = indexName;
    this.transformer = transformer;
    this.configuration = configuration;
  }

  @Override
  public ValueType getHandledValueType() {
    return transformer.config().valueType();
  }

  @Override
  public Class<AuditLogEntity> getEntityType() {
    return AuditLogEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<R> record) {
    if (!transformer.supports(record)) {
      return false;
    }

    return configuration.isEnabled(AuditLogInfo.of(record));
  }

  @Override
  public List<String> generateIds(final Record<R> record) {
    return List.of(record.getPartitionId() + "-" + record.getPosition());
  }

  @Override
  public AuditLogEntity createNewEntity(final String id) {
    return new AuditLogEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<R> record, final AuditLogEntity entity) {

    final var log = transformer.create(record);

    mapToEntity(log, entity);
  }

  @Override
  public void flush(final AuditLogEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private void mapToEntity(final AuditLogEntry log, final AuditLogEntity entity) {
    // generic fields
    entity
        .setEntityKey(log.getEntityKey())
        .setEntityType(mapEntityType(log.getEntityType()))
        .setCategory(mapCategory(log))
        .setOperationType(mapOperationType(log))
        .setActorType(mapActorType(log))
        .setActorId(log.getActor().actorId())
        .setAgentElementId(log.getAgent().map(Agent::getElementId).orElse(null))
        .setTenantScope(mapTenantScope(log))
        .setTenantId(log.getTenant().map(AuditLogTenant::tenantId).orElse(null))
        .setBatchOperationKey(log.getBatchOperationKey())
        .setBatchOperationType(log.getBatchOperationType())
        .setProcessInstanceKey(log.getProcessInstanceKey())
        .setEntityVersion(log.getEntityVersion())
        .setEntityValueType(log.getEntityValueType())
        .setEntityOperationIntent(log.getEntityOperationIntent())
        .setTimestamp(log.getTimestamp())
        .setAnnotation(log.getAnnotation());

    // transformer specific fields
    entity
        .setResult(mapResult(log))
        .setProcessDefinitionId(log.getProcessDefinitionId())
        .setProcessDefinitionKey(log.getProcessDefinitionKey())
        .setElementInstanceKey(log.getElementInstanceKey())
        .setJobKey(log.getJobKey())
        .setUserTaskKey(log.getUserTaskKey())
        .setDecisionEvaluationKey(log.getDecisionEvaluationKey())
        .setDecisionRequirementsId(log.getDecisionRequirementsId())
        .setDecisionRequirementsKey(log.getDecisionRequirementsKey())
        .setDecisionDefinitionId(log.getDecisionDefinitionId())
        .setDecisionDefinitionKey(log.getDecisionDefinitionKey())
        .setDeploymentKey(log.getDeploymentKey())
        .setFormKey(log.getFormKey())
        .setResourceKey(log.getResourceKey())
        .setRootProcessInstanceKey(log.getRootProcessInstanceKey())
        .setRelatedEntityKey(log.getRelatedEntityKey())
        .setRelatedEntityType(mapEntityType(log.getRelatedEntityType()))
        .setEntityDescription(log.getEntityDescription());
  }

  @VisibleForTesting
  public AuditLogTransformer<?> getTransformer() {
    return transformer;
  }

  private AuditLogEntityType mapEntityType(
      final io.camunda.search.entities.AuditLogEntity.AuditLogEntityType entityType) {
    return Objects.nonNull(entityType) ? AuditLogEntityType.valueOf(entityType.name()) : null;
  }

  private AuditLogOperationResult mapResult(final AuditLogEntry log) {
    return Objects.nonNull(log.getResult())
        ? AuditLogOperationResult.valueOf(log.getResult().name())
        : null;
  }

  private AuditLogOperationCategory mapCategory(final AuditLogEntry info) {
    return Objects.nonNull(info.getCategory())
        ? AuditLogOperationCategory.valueOf(info.getCategory().name())
        : null;
  }

  private AuditLogOperationType mapOperationType(final AuditLogEntry info) {
    return Objects.nonNull(info.getOperationType())
        ? AuditLogOperationType.valueOf(info.getOperationType().name())
        : null;
  }

  private AuditLogActorType mapActorType(final AuditLogEntry info) {
    return Objects.nonNull(info.getActor()) && Objects.nonNull(info.getActor().actorType())
        ? AuditLogActorType.valueOf(info.getActor().actorType().name())
        : null;
  }

  private AuditLogTenantScope mapTenantScope(final AuditLogEntry info) {
    return info.getTenant()
        .map(AuditLogTenant::scope)
        .map(t -> io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope.valueOf(t.name()))
        .orElse(AuditLogTenantScope.GLOBAL);
  }

  public static AuditLogHandlerBuilder builder(
      final String indexName, final AuditLogConfiguration auditLog) {
    return new AuditLogHandlerBuilder(indexName, auditLog);
  }

  public static class AuditLogHandlerBuilder {
    final Set<AuditLogHandler<?>> handlers = new HashSet<>();
    private final String indexName;
    private final AuditLogConfiguration auditLog;

    public AuditLogHandlerBuilder(final String indexName, final AuditLogConfiguration auditLog) {
      this.indexName = indexName;
      this.auditLog = auditLog;
    }

    public AuditLogHandlerBuilder addHandler(final AuditLogTransformer<?> transformer) {
      handlers.add(new AuditLogHandler<>(indexName, transformer, auditLog));
      return this;
    }

    public Set<AuditLogHandler<?>> build() {
      return new HashSet<>(handlers);
    }
  }
}
