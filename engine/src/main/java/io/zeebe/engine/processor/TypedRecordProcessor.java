/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import java.util.function.Consumer;

public interface TypedRecordProcessor<T extends UnifiedRecordValue>
    extends StreamProcessorLifecycleAware {

  /** @see #processRecord(TypedRecord, TypedResponseWriter, TypedStreamWriter, Consumer) */
  default void processRecord(
      TypedRecord<T> record, TypedResponseWriter responseWriter, TypedStreamWriter streamWriter) {}

  /** @see #processRecord(TypedRecord, TypedResponseWriter, TypedStreamWriter, Consumer) */
  default void processRecord(
      TypedRecord<T> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    processRecord(record, responseWriter, streamWriter);
  }

  /**
   * @param position the position of the current record to process
   * @param record the record to process
   * @param responseWriter the default side effect that can be used for sending responses. {@link
   *     TypedResponseWriter#flush()} must not be called in this method.
   * @param streamWriter
   * @param sideEffect consumer to replace the default side effect (response writer). Can be used to
   *     implement other types of side effects or composite side effects. If a composite side effect
   *     involving the response writer is used, {@link TypedResponseWriter#flush()} must be called
   *     in the {@link SideEffectProducer} implementation.
   */
  default void processRecord(
      long position,
      TypedRecord<T> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    processRecord(record, responseWriter, streamWriter, sideEffect);
  }
}
