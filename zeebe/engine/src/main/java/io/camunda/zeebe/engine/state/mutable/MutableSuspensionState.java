/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;

/** Write access to the process-instance suspend/resume state (POC #56552). */
public interface MutableSuspensionState extends SuspensionState {

  /** Marks the process instance as suspended. */
  void suspend(long processInstanceKey);

  /** Removes the suspended marker and discards any buffered commands for the process instance. */
  void resume(long processInstanceKey);

  /**
   * Appends a diverted forward-progress element command to the FIFO buffer. {@code seq} must be
   * monotonically increasing per process instance and deterministic on replay (e.g. a key issued by
   * {@code KeyGenerator} for the buffering event) to provide FIFO ordering. The {@code record} must
   * carry the original element-command intent via {@link
   * ProcessInstanceRecord#setBufferedElementIntent(int)}.
   */
  void bufferCommand(long processInstanceKey, long seq, ProcessInstanceRecord record);

  /** Discards all buffered commands for the process instance without removing the marker. */
  void clearBuffer(long processInstanceKey);
}
