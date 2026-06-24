/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles a single command intent — the event-bridge counterpart of the Zeebe engine's {@code
 * TypedRecordProcessor}. Registered per {@code (ValueType, Intent)} in {@link
 * RecordProcessingEngine} via {@link RecordProcessors#onCommand}, it turns a command into follow-up
 * events. As in the engine, a processor receives the {@link StateWriter} (and any other writers) at
 * <em>construction</em> — not per call — and uses it to append follow-up events, which the writer
 * applies through the engine's {@link EventAppliers} in the same step. Processors hold no state of
 * their own.
 *
 * @param <T> the command's record value type
 */
@FunctionalInterface
public interface TypedRecordProcessor<T extends UnifiedRecordValue> {

  void processRecord(TypedRecord<T> command);
}
