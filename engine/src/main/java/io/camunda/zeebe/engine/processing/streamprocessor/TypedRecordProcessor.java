/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;

// todo (#8002): remove TypedStreamWriter from this interface's method signatures
// After the migration, none of these should be in use anymore and replaced by the CommandWriter and
// StateWriter passed along to the constructors of the concrete processors.
public interface TypedRecordProcessor<T extends UnifiedRecordValue>
    extends StreamProcessorLifecycleAware {

  /**
   * @param record the record to process
   * @param responseWriter the default side effect that can be used for sending responses. {@link
   *     TypedResponseWriter#flush()} must not be called in this method.
   * @param streamWriter
   */
  default void processRecord(
      final TypedRecord<T> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {}
}
