/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Set;

/**
 * Transforms record-specific data into audit log entities.
 *
 * <p>Implementations specify which intents and rejection types they support, and provide custom
 * transformation logic to populate audit log fields specific to their record type.
 *
 * @param <R> the record value type for this transformer
 */
public interface AuditLogTransformer<R extends RecordValue, T> {

  TransformerConfig config();

  void transform(Record<R> record, final T entity);

  default boolean supports(final Record record) {
    return config().supports(record);
  }

  public record TransformerConfig(
      ValueType valueType,
      Set<Intent> supportedIntents,
      Set<Intent> supportedRejections,
      Set<RejectionType> supportedRejectionTypes) {

    public static TransformerConfig with(final ValueType valueType) {
      return new TransformerConfig(valueType, Set.of(), Set.of(), Set.of());
    }

    public TransformerConfig withIntents(final Intent... intents) {
      return new TransformerConfig(
          valueType(), Set.of(intents), supportedRejections(), supportedRejectionTypes);
    }

    public TransformerConfig withRejections(
        final Intent rejectionIntent, final RejectionType... rejectionTypes) {
      return new TransformerConfig(
          valueType(), supportedIntents(), Set.of(rejectionIntent), Set.of(rejectionTypes));
    }

    boolean supports(final Record record) {
      switch (record.getRecordType()) {
        case EVENT:
          return supportedIntents().contains(record.getIntent());
        case COMMAND_REJECTION:
          return supportedRejections().contains(record.getIntent())
              && supportedRejectionTypes().contains(record.getRejectionType());
        default:
          return false;
      }
    }
  }
}
