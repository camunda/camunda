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
 * RecordProcessingEngine}, it turns a command into follow-up events with the supplied {@link
 * StateWriter} (which both writes the event and applies it through the engine's {@link
 * EventAppliers}). Processors hold no state of their own — all state mutation happens in the event
 * appliers.
 *
 * @param <T> the command's record value type
 */
@FunctionalInterface
public interface TypedRecordProcessor<T extends UnifiedRecordValue> {

  void processRecord(TypedRecord<T> command, StateWriter stateWriter);
}
