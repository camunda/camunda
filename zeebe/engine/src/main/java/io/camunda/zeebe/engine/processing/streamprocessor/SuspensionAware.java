/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Implemented by {@link TypedRecordProcessor}s whose commands are related to a process instance, in
 * order to classify how the primary suspension gate (see {@code Engine#process}) should treat the
 * command while the target process instance carries a suspension marker ({@code SUSPENDED} or
 * {@code RESUMING}).
 *
 * @param <T> the record value type processed by the implementing {@link TypedRecordProcessor}
 */
public interface SuspensionAware<T extends UnifiedRecordValue> {

  /**
   * Classifies how the suspension gate should treat the given command while its target is
   * suspended.
   *
   * @param record the command record being classified
   * @return the {@link SuspensionBehavior} to apply; never {@code null}
   */
  SuspensionBehavior suspensionBehavior(final TypedRecord<T> record);

  enum SuspensionBehavior {
    /** Process the command immediately, regardless of the suspension marker. */
    PROCESS,
    /** Reject the command while any suspension marker (SUSPENDED or RESUMING) is present. */
    REJECT,
    /**
     * Buffer the command while {@code SUSPENDED}; pass it through while {@code RESUMING} so that
     * commands drained during resume can actually execute.
     */
    BUFFER
  }
}
