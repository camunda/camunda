/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import java.util.function.Consumer;

/**
 * Read access to the process-instance suspend/resume state (POC #56552).
 *
 * <p>A process instance is "suspended" when a marker exists for its key. While suspended,
 * forward-progress BPMN element commands are diverted into a FIFO buffer keyed by {@code
 * (processInstanceKey, logPosition)} and replayed in order on resume.
 */
public interface SuspensionState {

  /** Returns {@code true} if the given process instance is currently suspended. */
  boolean isSuspended(long processInstanceKey);

  /**
   * Visits the buffered element commands for the given process instance in FIFO (insertion) order.
   * The visited {@link ProcessInstanceRecord} is backed by a shared buffer — consume it within the
   * visitor, do not cache it. The original element-command intent is available via {@link
   * ProcessInstanceRecord#getBufferedElementIntent()}.
   */
  void forEachBufferedCommand(long processInstanceKey, Consumer<ProcessInstanceRecord> visitor);
}
