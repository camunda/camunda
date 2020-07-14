/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETING;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_TERMINATING;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.EVENT_OCCURRED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN;

import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class WorkflowInstanceLifecycle {

  private static final EnumSet<WorkflowInstanceIntent> ELEMENT_INSTANCE_STATES =
      EnumSet.of(
          ELEMENT_ACTIVATING,
          ELEMENT_ACTIVATED,
          ELEMENT_COMPLETING,
          ELEMENT_COMPLETED,
          ELEMENT_TERMINATING,
          ELEMENT_TERMINATED);

  private static final EnumSet<WorkflowInstanceIntent> FINAL_ELEMENT_INSTANCE_STATES =
      EnumSet.of(ELEMENT_COMPLETED, ELEMENT_TERMINATED);

  private static final EnumSet<WorkflowInstanceIntent> TERMINATABLE_STATES =
      EnumSet.of(ELEMENT_ACTIVATING, ELEMENT_ACTIVATED, ELEMENT_COMPLETING);

  private static final Map<WorkflowInstanceIntent, Set<WorkflowInstanceIntent>> TRANSITION_RULES =
      new EnumMap<>(WorkflowInstanceIntent.class);

  static {
    TRANSITION_RULES.put(ELEMENT_ACTIVATING, EnumSet.of(ELEMENT_ACTIVATED, ELEMENT_TERMINATING));
    TRANSITION_RULES.put(ELEMENT_ACTIVATED, EnumSet.of(ELEMENT_COMPLETING, ELEMENT_TERMINATING));
    TRANSITION_RULES.put(ELEMENT_COMPLETING, EnumSet.of(ELEMENT_COMPLETED, ELEMENT_TERMINATING));
    TRANSITION_RULES.put(ELEMENT_TERMINATING, EnumSet.of(ELEMENT_TERMINATED));
    TRANSITION_RULES.put(ELEMENT_COMPLETED, EnumSet.of(SEQUENCE_FLOW_TAKEN));
    TRANSITION_RULES.put(ELEMENT_TERMINATED, Collections.emptySet());
    TRANSITION_RULES.put(EVENT_OCCURRED, EnumSet.of(ELEMENT_COMPLETING, ELEMENT_TERMINATING));
    TRANSITION_RULES.put(SEQUENCE_FLOW_TAKEN, EnumSet.of(ELEMENT_ACTIVATING));
  }

  private WorkflowInstanceLifecycle() {}

  public static boolean canTransition(
      final WorkflowInstanceIntent from, final WorkflowInstanceIntent to) {
    return TRANSITION_RULES.get(from).contains(to);
  }

  public static boolean isFinalState(final WorkflowInstanceIntent state) {
    return FINAL_ELEMENT_INSTANCE_STATES.contains(state);
  }

  public static boolean isInitialState(final WorkflowInstanceIntent state) {
    return state == ELEMENT_ACTIVATING;
  }

  public static boolean isElementInstanceState(final WorkflowInstanceIntent state) {
    return ELEMENT_INSTANCE_STATES.contains(state);
  }

  public static boolean isTokenState(final WorkflowInstanceIntent state) {
    return !isElementInstanceState(state);
  }

  public static boolean canTerminate(final WorkflowInstanceIntent currentState) {
    return TERMINATABLE_STATES.contains(currentState);
  }

  public static boolean isActive(final WorkflowInstanceIntent currentState) {
    return currentState == ELEMENT_ACTIVATED;
  }

  public static boolean isTerminating(final WorkflowInstanceIntent currentState) {
    return currentState == ELEMENT_TERMINATING;
  }
}
