/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
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

  Logger LOG = LoggerFactory.getLogger(AuditLogTransformer.class);

  TransformerConfig config();

  default AuditLogEntry create(final Record<R> record) {
    final AuditLogEntry log = AuditLogEntry.of(record);

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
        log.setEntityDescription(record.getRejectionType().name());
      } else {
        log.setResult(io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult.SUCCESS);
      }
    }

    return log;
  }

  default void transform(final Record<R> record, final AuditLogEntry log) {}

  default boolean supports(final Record<?> record) {
    return config().supports(record);
  }

  default boolean triggersCleanUp(final Record<R> record) {
    return config().dataCleanupIntents().contains(record.getIntent());
  }

  public record TransformerConfig(
      ValueType valueType,
      Set<Intent> supportedIntents,
      Set<Intent> supportedRejections,
      Set<RejectionType> supportedRejectionTypes,
      Set<Intent> dataCleanupIntents) {

    public static TransformerConfig with(final ValueType valueType) {
      return new TransformerConfig(valueType, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public TransformerConfig withIntents(final Intent... intents) {
      return new TransformerConfig(
          valueType(),
          Set.of(intents),
          supportedRejections(),
          supportedRejectionTypes,
          dataCleanupIntents());
    }

    public TransformerConfig withRejections(
        final Intent rejectionIntent, final RejectionType... rejectionTypes) {
      return new TransformerConfig(
          valueType(),
          supportedIntents(),
          Set.of(rejectionIntent),
          Set.of(rejectionTypes),
          dataCleanupIntents());
    }

    public TransformerConfig withRejections(final Intent... rejectionIntents) {
      return new TransformerConfig(
          valueType(),
          supportedIntents(),
          Set.of(rejectionIntents),
          supportedRejectionTypes(),
          dataCleanupIntents());
    }

    public TransformerConfig withRejectionTypes(final RejectionType... rejectionTypes) {
      return new TransformerConfig(
          valueType(),
          supportedIntents(),
          supportedRejections(),
          Set.of(rejectionTypes),
          dataCleanupIntents());
    }

    public TransformerConfig withDataCleanupIntents(final Intent... dataCleanupIntents) {
      return new TransformerConfig(
          valueType(),
          supportedIntents(),
          supportedRejections(),
          supportedRejectionTypes(),
          Set.of(dataCleanupIntents));
    }

    boolean supports(final Record<?> record) {
      return switch (record.getRecordType()) {
        case EVENT -> supportedIntents().contains(record.getIntent());
        case COMMAND_REJECTION ->
            supportedRejections().contains(record.getIntent())
                && supportedRejectionTypes().contains(record.getRejectionType());
        default -> false;
      };
    }
  }
}
