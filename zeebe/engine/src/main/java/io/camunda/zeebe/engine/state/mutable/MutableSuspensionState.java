/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBufferedCommandRecord;

public interface MutableSuspensionState extends SuspensionState {

  /** Sets (inserts or overwrites) the suspension marker for the process instance. */
  void setSuspensionState(long processInstanceKey, State state);

  /** Removes the suspension marker for the process instance. No-op if not present. */
  void removeSuspensionState(long processInstanceKey);

  /**
   * Appends a buffered command under the given (KeyGenerator-issued) bufferedCommandKey. The
   * process instance it belongs to is read from {@code command.getProcessInstanceKey()} — not a
   * separate parameter — so a mismatched caller-supplied key can never desync the secondary FIFO
   * index from the record it was built from. Callers are responsible for key generation and
   * uniqueness.
   */
  void bufferCommand(long bufferedCommandKey, ProcessInstanceBufferedCommandRecord command);

  /**
   * Removes a single buffered command entry. No-op if it does not exist. The process instance it
   * belongs to is read from the stored record itself, mirroring {@link #bufferCommand} — there is
   * no separate parameter to desync from it.
   */
  void removeBufferedCommand(long bufferedCommandKey);

  /** Removes all remaining buffered commands for the process instance. */
  void clearBufferedCommands(long processInstanceKey);
}
