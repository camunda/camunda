/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface SuspensionState {

  enum State {
    SUSPENDED,
    RESUMING,
    SUSPENDING
  }

  /**
   * @return the current suspension marker for the process instance, or {@code null} if it has no
   *     suspension marker
   */
  @Nullable State getSuspensionState(long processInstanceKey);

  /**
   * @return {@code true} while the process instance is {@link State#SUSPENDED} or {@link
   *     State#RESUMING}; {@code false} while {@link State#SUSPENDING} or without a marker. The
   *     marker remains while buffered commands drain during resuming. Use {@link
   *     #getSuspensionState(long)} to check for any marker or distinguish the states.
   */
  boolean isSuspended(long processInstanceKey);

  /**
   * Visits every buffered command for the given process instance in ascending {@code
   * bufferedCommandKey} order. This is FIFO (insertion) order because {@code bufferedCommandKey} is
   * expected to be KeyGenerator-issued and therefore monotonically increasing.
   */
  void visitBufferedCommands(long processInstanceKey, BufferedCommandVisitor visitor);

  /**
   * Reads the head of the process instance's FIFO buffer without scanning the rest of it, so that
   * draining one command per {@code DRAIN} cycle stays cheap no matter how much is buffered.
   *
   * @return the oldest buffered command after {@code afterCommandKey}, or {@link Optional#empty()}
   *     if none remain
   */
  Optional<BufferedCommand> findNextBufferedCommand(long processInstanceKey, long afterCommandKey);

  /**
   * Counts the buffered commands for the given process instance without reading their values,
   * unlike {@link #visitBufferedCommands} which deserializes every record. Use this when only the
   * count is needed, e.g. for a dropped-commands metric on termination.
   */
  int countBufferedCommands(long processInstanceKey);

  @FunctionalInterface
  interface BufferedCommandVisitor {
    void visit(long bufferedCommandKey, BufferedCommandRecordValue command);
  }

  /** A buffered command together with the key it is stored under. */
  record BufferedCommand(long key, BufferedCommandRecord command) {}
}
