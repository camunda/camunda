/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.record.value.ProcessInstanceBufferedCommandRecordValue;

public interface SuspensionState {

  enum State {
    SUSPENDED,
    RESUMING
  }

  /**
   * @return the current suspension marker for the process instance, or {@code null} if it has no
   *     suspension marker
   */
  State getSuspensionState(long processInstanceKey);

  /**
   * @return {@code true} if the process instance has any suspension marker (either {@link
   *     State#SUSPENDED} or {@link State#RESUMING}); {@code false} if it has none. The marker is
   *     only removed once resuming has fully drained the buffer.
   *     <p>This reflects marker <em>presence</em>, not a specific state, and does not imply {@link
   *     State#SUSPENDED} and {@link State#RESUMING} should be gated identically — e.g. the primary
   *     buffering gate must buffer forward-progress commands while {@code SUSPENDED} but pass them
   *     through while {@code RESUMING}. Callers that need to distinguish the two should branch on
   *     {@link #getSuspensionState} instead.
   */
  boolean isSuspended(long processInstanceKey);

  /**
   * Visits every buffered command for the given process instance in ascending {@code
   * bufferedCommandKey} order. This is FIFO (insertion) order because {@code bufferedCommandKey} is
   * expected to be KeyGenerator-issued and therefore monotonically increasing.
   */
  void visitBufferedCommands(long processInstanceKey, BufferedCommandVisitor visitor);

  @FunctionalInterface
  interface BufferedCommandVisitor {
    void visit(long bufferedCommandKey, ProcessInstanceBufferedCommandRecordValue command);
  }
}
