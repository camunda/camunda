/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import java.util.Map;

/**
 * Represents a single waiting-state entry exported from the engine.
 *
 * <p>A waiting state captures a process element that is currently paused, awaiting an external
 * signal to continue (a job worker, message correlation, user task completion, timer firing, etc.).
 * The {@link #details} map carries wait-state-specific attributes (e.g. job type, message name, due
 * date) and is serialized by each backend exporter according to its own schema.
 */
public record WaitStateEntry(
    long rootProcessInstanceKey,
    long processInstanceKey,
    long elementInstanceKey,
    String elementId,
    WaitStateElementType elementType,
    WaitStateType waitStateType,
    Map<String, Object> details,
    String tenantId,
    long partitionId) {

  public enum WaitStateElementType {
    SERVICE_TASK,
    SEND_TASK,
    RECEIVE_TASK,
    USER_TASK,
    BUSINESS_RULE_TASK,
    SCRIPT_TASK,
    INTERMEDIATE_CATCH_EVENT,
    BOUNDARY_EVENT,
    EVENT_BASED_GATEWAY,
    CALL_ACTIVITY
  }

  public enum WaitStateType {
    JOB,
    MESSAGE,
    USER_TASK,
    TIMER,
    SIGNAL,
    INCIDENT,
    CALL_ACTIVITY
  }
}
