/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

/**
 * Immutable value object capturing the time bounds and lifecycle state of one generated process
 * instance.
 *
 * <p>Extracted as a top-level type so both {@link ZeebeProcessDataGenerator} (which constructs it)
 * and {@link FlowNodeEmitter} (which reads it) can reference it without coupling through the
 * generator class.
 */
record InstanceWindow(
    long startMs, long endMs, boolean isActive, boolean isTerminated, boolean hasIncident) {

  /** The correct {@link ProcessInstanceIntent} to close a node given this instance's outcome. */
  ProcessInstanceIntent endIntent() {
    return isTerminated ? ELEMENT_TERMINATED : ELEMENT_COMPLETED;
  }
}
