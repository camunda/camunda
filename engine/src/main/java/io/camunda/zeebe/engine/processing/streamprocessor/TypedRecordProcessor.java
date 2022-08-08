/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import java.util.function.Consumer;

public interface TypedRecordProcessor<T extends UnifiedRecordValue> {

  default void processRecord(final TypedRecord<T> record) {}

  /**
   * @see #processRecord(TypedRecord, Consumer)
   */
  default void processRecord(
      final TypedRecord<T> record, final Consumer<SideEffectProducer> sideEffect) {
    processRecord(record);
  }
}
