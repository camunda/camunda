/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.BatchOperation;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.DateUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  public static final Logger LOG = LoggerFactory.getLogger(AuditLogHandler.class);
  protected static final int ID_LENGTH = 32;

  private final String indexName;
  private final AuditLogTransformer<R, AuditLogEntity> transformer;
  private final AuditLogConfiguration configuration;

  public AuditLogHandler(
      final String indexName,
      final AuditLogTransformer<R, AuditLogEntity> transformer,
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
    final var info = AuditLogInfo.of(record);

    return transformer.supports(record) && configuration.isEnabled(info);
  }

  @Override
  public List<String> generateIds(final Record<R> record) {
    return List.of(RandomStringUtils.insecure().nextAlphanumeric(ID_LENGTH));
  }

  @Override
  public AuditLogEntity createNewEntity(final String id) {
    return new AuditLogEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<R> record, final AuditLogEntity entity) {
    final AuditLogInfo info = AuditLogInfo.of(record);

    entity
        .setEntityKey(String.valueOf(record.getKey()))
        .setEntityType(mapEntityType(info))
        .setCategory(mapCategory(info))
        .setOperationType(mapOperationType(info))
        .setActorType(mapActorType(info))
        .setActorId(info.actor().actorId())
        .setBatchOperationKey(info.batchOperation().map(BatchOperation::key).orElse(null))
        .setEntityVersion(record.getRecordVersion())
        .setEntityValueType(record.getValueType().value())
        .setEntityOperationIntent(record.getIntent().value())
        .setTimestamp(DateUtil.toOffsetDateTime(record.getTimestamp()));

    if (RecordType.COMMAND_REJECTION.equals(record.getRecordType())) {
      entity.setResult(AuditLogOperationResult.FAIL);
      // TODO: set rejection type and reason to AuditLogEntity#details
    } else {
      entity.setResult(AuditLogOperationResult.SUCCESS);
      transformer.transform(record, entity);
    }
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

  @VisibleForTesting
  public AuditLogTransformer<?, ?> getTransformer() {
    return transformer;
  }

  private AuditLogEntityType mapEntityType(final AuditLogInfo info) {
    return Objects.nonNull(info.entityType())
        ? AuditLogEntityType.valueOf(info.entityType().name())
        : null;
  }

  private AuditLogOperationCategory mapCategory(final AuditLogInfo info) {
    return Objects.nonNull(info.category())
        ? AuditLogOperationCategory.valueOf(info.category().name())
        : null;
  }

  private AuditLogOperationType mapOperationType(final AuditLogInfo info) {
    return Objects.nonNull(info.operationType())
        ? AuditLogOperationType.valueOf(info.operationType().name())
        : null;
  }

  private AuditLogActorType mapActorType(final AuditLogInfo info) {
    return Objects.nonNull(info.actor()) && Objects.nonNull(info.actor().actorType())
        ? AuditLogActorType.valueOf(info.actor().actorType().name())
        : null;
  }

  public static AuditLogHandlerBuilder builder(
      final String indexName, final AuditLogConfiguration auditLog) {
    return new AuditLogHandlerBuilder(indexName, auditLog);
  }

  public static class AuditLogHandlerBuilder {
    final Set<AuditLogHandler> handlers = new HashSet<>();
    private final String indexName;
    private final AuditLogConfiguration auditLog;

    public AuditLogHandlerBuilder(final String indexName, final AuditLogConfiguration auditLog) {
      this.indexName = indexName;
      this.auditLog = auditLog;
    }

    public AuditLogHandlerBuilder addHandler(final AuditLogTransformer transformer) {
      handlers.add(new AuditLogHandler<>(indexName, transformer, auditLog));
      return this;
    }

    public Set<AuditLogHandler> build() {
      return new HashSet<>(handlers);
    }
  }
}
