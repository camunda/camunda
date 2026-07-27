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
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;
import java.util.Objects;

abstract class AbstractAuditLogHandler<T extends ExporterEntity<T>, R extends RecordValue>
    implements ExportHandler<T, R> {
  protected final String indexName;
  protected final AuditLogTransformer<R> transformer;
  protected final AuditLogConfiguration configuration;

  public AbstractAuditLogHandler(
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
  public void flush(final TargetIndex index, final T entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(index, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  protected AuditLogEntityType mapEntityType(
      final io.camunda.search.entities.AuditLogEntity.AuditLogEntityType entityType) {
    return Objects.nonNull(entityType) ? AuditLogEntityType.valueOf(entityType.name()) : null;
  }
}
