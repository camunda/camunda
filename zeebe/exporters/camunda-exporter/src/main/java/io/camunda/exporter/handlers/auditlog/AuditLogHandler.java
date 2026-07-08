/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import static java.util.Optional.ofNullable;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.auditlog.AuditLogHandler.AuditLogBatch;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogCleanupEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogTenant;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.Objects;

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
public class AuditLogHandler<R extends RecordValue>
    extends AbstractAuditLogHandler<AuditLogBatch, R> {

  private final String indexName;
  private final String auditLogCleanupIndexName;

  public AuditLogHandler(
      final String indexName,
      final String auditLogCleanupIndexName,
      final AuditLogTransformer<R> transformer,
      final AuditLogConfiguration configuration) {
    super(transformer, configuration);
    this.indexName = indexName;
    this.auditLogCleanupIndexName = auditLogCleanupIndexName;
  }

  @Override
  public Class<AuditLogBatch> getEntityType() {
    return AuditLogBatch.class;
  }

  @Override
  public AuditLogBatch createNewEntity(final String id) {
    return new AuditLogBatch(id);
  }

  @Override
  public void updateEntity(final Record<R> record, final AuditLogBatch batch) {

    final var log = transformer.create(record);

    final var entity = new AuditLogEntity().setId(batch.getId());
    batch.setAuditLogEntity(mapToEntity(log, entity));

    // archiving of decision instances is done by the StandaloneDecisionArchiverJob
    final io.camunda.search.entities.AuditLogEntity.AuditLogEntityType auditLogEntityType =
        AuditLogEntry.getEntityType(record);
    final boolean decisionLog =
        auditLogEntityType == io.camunda.search.entities.AuditLogEntity.AuditLogEntityType.DECISION;
    if (transformer.triggersCleanUp(record) && !decisionLog) {
      final var cleanupEntity = new AuditLogCleanupEntity().setId(batch.getId());
      batch.setAuditLogCleanupEntity(mapToCleanupEntity(record, auditLogEntityType, cleanupEntity));
    }
  }

  @Override
  public void flush(final AuditLogBatch batch, final BatchRequest batchRequest)
      throws PersistenceException {

    batchRequest.add(indexName, batch.auditLogEntity);
    ofNullable(batch.auditLogCleanupEntity)
        .ifPresent(e -> batchRequest.add(auditLogCleanupIndexName, e));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private AuditLogCleanupEntity mapToCleanupEntity(
      final Record<R> record,
      final io.camunda.search.entities.AuditLogEntity.AuditLogEntityType auditLogEntityType,
      final AuditLogCleanupEntity cleanupEntity) {
    return cleanupEntity
        .setKey(AuditLogEntry.getEntityKey(record))
        .setKeyField(AuditLogTemplate.ENTITY_KEY)
        .setEntityType(mapEntityType(auditLogEntityType))
        .setPartitionId(record.getPartitionId());
  }

  private AuditLogEntity mapToEntity(final AuditLogEntry log, final AuditLogEntity entity) {
    // generic fields
    entity
        .setEntityKey(log.getEntityKey())
        .setEntityType(mapEntityType(log.getEntityType()))
        .setCategory(mapCategory(log))
        .setOperationType(mapOperationType(log))
        .setActorType(mapActorType(log))
        .setActorId(log.getActor().actorId())
        .setAgentElementId(log.getAgent().map(Agent::getElementId).orElse(null))
        .setInboundChannelType(
            log.getInboundChannelType() != null ? log.getInboundChannelType().name() : null)
        .setInboundChannelToolName(log.getInboundChannelToolName())
        .setTenantScope(mapTenantScope(log))
        .setTenantId(log.getTenant().map(AuditLogTenant::tenantId).orElse(null))
        .setBatchOperationKey(log.getBatchOperationKey())
        .setBatchOperationType(log.getBatchOperationType())
        .setProcessInstanceKey(log.getProcessInstanceKey())
        .setEntityVersion(log.getEntityVersion())
        .setEntityValueType(log.getEntityValueType())
        .setEntityOperationIntent(log.getEntityOperationIntent())
        .setTimestamp(log.getTimestamp());

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

    return entity;
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

  public static final class AuditLogBatch implements ExporterEntity<AuditLogBatch> {
    private final String id;
    private AuditLogEntity auditLogEntity;
    private AuditLogCleanupEntity auditLogCleanupEntity;

    public AuditLogBatch(final String id) {
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public AuditLogBatch setId(final String id) {
      throw new UnsupportedOperationException("Not allowed to set an id");
    }

    public AuditLogEntity getAuditLogEntity() {
      return auditLogEntity;
    }

    public void setAuditLogEntity(final AuditLogEntity auditLogEntity) {
      this.auditLogEntity = auditLogEntity;
    }

    public AuditLogCleanupEntity getAuditLogCleanupEntity() {
      return auditLogCleanupEntity;
    }

    public void setAuditLogCleanupEntity(final AuditLogCleanupEntity auditLogCleanupEntity) {
      this.auditLogCleanupEntity = auditLogCleanupEntity;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, auditLogEntity, auditLogCleanupEntity);
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null || obj.getClass() != getClass()) {
        return false;
      }
      final var that = (AuditLogBatch) obj;
      return Objects.equals(id, that.id)
          && Objects.equals(auditLogEntity, that.auditLogEntity)
          && Objects.equals(auditLogCleanupEntity, that.auditLogCleanupEntity);
    }
  }
}
