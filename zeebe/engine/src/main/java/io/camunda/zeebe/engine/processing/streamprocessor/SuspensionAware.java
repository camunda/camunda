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
 * Opt-in hook for {@link TypedRecordProcessor}s whose commands target a process instance.
 *
 * <p>When the target instance is {@code SUSPENDED} or {@code RESUMING}, the primary gate (see
 * {@code Engine#process}) calls {@link #onSuspended} or {@link #onResuming} instead of {@link
 * TypedRecordProcessor#processRecord}. Each hook can write follow-up events that belong to that
 * state, then returns a {@link SuspensionAction} telling the gate whether to process, reject, or
 * buffer the command.
 *
 * @param <T> the record value type processed by the implementing {@link TypedRecordProcessor}
 */
public interface SuspensionAware<T extends UnifiedRecordValue> {

  /**
   * Handles the command while the target instance is {@code SUSPENDED}, instead of processing it.
   * Write any events that must accompany this outcome (for example {@code Timer.SUSPENDED} when
   * dropping a due-date index), then return how the gate should treat the command.
   *
   * @param record the command being handled
   * @return the {@link SuspensionAction} the gate should apply; never {@code null}
   */
  SuspensionAction onSuspended(final TypedRecord<T> record);

  /**
   * Handles the command when the target instance is {@code RESUMING}, instead of processing it.
   * Write any events that must accompany this outcome (for example {@code Timer.RESUMED} before a
   * drained trigger runs), then return how the gate should treat the command.
   *
   * <p>The returned action be compatible {@link #onSuspended} for command execution. {@link
   * SuspensionAction#BUFFER} is not allowed: buffered commands must drain.
   *
   * @param record the command being handled
   * @return {@link SuspensionAction#PROCESS} or {@link SuspensionAction#REJECT}; never {@code null}
   *     or {@link SuspensionAction#BUFFER}
   */
  SuspensionAction onResuming(final TypedRecord<T> record);

  static SuspensionAction bufferInternalOnly(final TypedRecord<?> record) {
    return record.isInternalCommand() ? SuspensionAction.BUFFER : SuspensionAction.REJECT;
  }

  static SuspensionAction processInternalOnly(final TypedRecord<?> record) {
    return record.isInternalCommand() ? SuspensionAction.PROCESS : SuspensionAction.REJECT;
  }

  enum SuspensionAction {
    /** Process the command immediately, regardless of the suspension marker. */
    PROCESS,
    /** Reject the command while any suspension marker (SUSPENDED or RESUMING) is present. */
    REJECT,
    /** Buffer the command while {@code SUSPENDED}. Must not be returned by {@link #onResuming}. */
    BUFFER
  }
}
