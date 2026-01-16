/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms record-specific data into audit log entities.
 *
 * <p>Implementations specify which intents and rejection types they support, and provide custom
 * transformation logic to populate audit log fields specific to their record type.
 *
 * @param <R> the record value type for this transformer
 */
public interface AuditLogTransformer<R extends RecordValue> {

  public static final Logger LOG = LoggerFactory.getLogger(AuditLogTransformer.class);

  TransformerConfig config();

  default AuditLogEntry create(final Record<R> record) {
    final AuditLogEntry log = AuditLogEntry.of(record);

    log.setOperationType(config().auditLogOperationType(record.getIntent()))
        .setCategory(config().category())
        .setEntityType(config().entityType());

    try {
      transform(record, log);
    } catch (final Exception e) {
      LOG.error(
          "Error transforming audit log entity for record with key {}: {}",
          record.getKey(),
          e.getMessage(),
          e);
    }

    if (log.getResult() == null) {
      if (RecordType.COMMAND_REJECTION.equals(record.getRecordType())) {
        log.setResult(io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.FAIL);
      } else {
        log.setResult(io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.SUCCESS);
      }
    }

    return log;
  }

  default void transform(final Record<R> record, final AuditLogEntry log) {}

  default boolean supports(final Record record) {
    return config().supports(record);
  }

  public record TransformerConfig(
      ValueType valueType,
      Set<Tuple<Intent, AuditLogOperationType>> supportedIntents,
      Set<Intent> supportedRejections,
      Set<RejectionType> supportedRejectionTypes,
      AuditLogOperationCategory category,
      AuditLogEntityType entityType) {

    public AuditLogOperationType auditLogOperationType(final Intent intent) {
      return supportedIntents.stream()
          .filter(t -> t.getLeft() == intent)
          .map(Tuple::getRight)
          .findAny()
          .orElseThrow();
    }

    public static TransformerConfig with(final ValueType valueType) {
      return new TransformerConfig(valueType, Set.of(), Set.of(), Set.of(), null, null);
    }

    public TransformerConfig withIntents(final Intent... intent) {
      return new TransformerConfig(
          valueType(),
          Set.of(),
          supportedRejections(),
          supportedRejectionTypes,
          category(),
          entityType);
    }

    public TransformerConfig withIntents(final Tuple<Intent, AuditLogOperationType>... intents) {
      return new TransformerConfig(
          valueType(),
          Set.of(intents),
          supportedRejections(),
          supportedRejectionTypes,
          category(),
          entityType);
    }

    public TransformerConfig withRejections(
        final Intent rejectionIntent, final RejectionType... rejectionTypes) {
      return new TransformerConfig(
          valueType(),
          supportedIntents(),
          Set.of(rejectionIntent),
          Set.of(rejectionTypes),
          category(),
          entityType);
    }

    public TransformerConfig withRejections(final Intent... rejectionIntents) {
      return new TransformerConfig(
          valueType(),
          supportedIntents(),
          Set.of(rejectionIntents),
          supportedRejectionTypes(),
          category(),
          entityType);
    }

    public TransformerConfig withRejectionTypes(final RejectionType... rejectionTypes) {
      return new TransformerConfig(
          valueType(),
          supportedIntents(),
          supportedRejections(),
          Set.of(rejectionTypes),
          category(),
          entityType);
    }

    public TransformerConfig withCategory(final AuditLogOperationCategory category) {
      return new TransformerConfig(
          valueType(),
          supportedIntents(),
          supportedRejections(),
          supportedRejectionTypes(),
          category,
          entityType());
    }

    public TransformerConfig withEntityType(final AuditLogEntityType entityType) {
      return new TransformerConfig(
          valueType(),
          supportedIntents(),
          supportedRejections(),
          supportedRejectionTypes(),
          category(),
          entityType);
    }

    boolean supports(final Record record) {
      switch (record.getRecordType()) {
        case EVENT:
          return supportedIntents().stream()
              .map(Tuple::getLeft)
              .anyMatch(i -> i == record.getIntent());
        case COMMAND_REJECTION:
          return supportedRejections().contains(record.getIntent())
              && supportedRejectionTypes().contains(record.getRejectionType());
        default:
          return false;
      }
    }
  }
}
