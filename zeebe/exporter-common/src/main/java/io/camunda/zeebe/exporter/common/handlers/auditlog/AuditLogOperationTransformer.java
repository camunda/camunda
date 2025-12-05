/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.handlers.auditlog;

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
 * @param <T> the intent type for this transformer
 * @param <R> the record value type for this transformer
 */
public interface AuditLogOperationTransformer<T extends Intent, R extends RecordValue, E> {

  ValueType getValueType();

  Set<T> getSupportedIntents();

  Set<T> getSupportedCommandRejections();

  Set<RejectionType> getSupportedRejectionTypes();

  void transform(final E entity, Record<R> record);
}
