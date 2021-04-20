/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn;

import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETING;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATING;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN;

import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ProcessInstanceLifecycle {

  private static final EnumSet<ProcessInstanceIntent> ELEMENT_INSTANCE_STATES =
      EnumSet.of(
          ELEMENT_ACTIVATING,
          ELEMENT_ACTIVATED,
          ELEMENT_COMPLETING,
          ELEMENT_COMPLETED,
          ELEMENT_TERMINATING,
          ELEMENT_TERMINATED);

  private static final EnumSet<ProcessInstanceIntent> FINAL_ELEMENT_INSTANCE_STATES =
      EnumSet.of(ELEMENT_COMPLETED, ELEMENT_TERMINATED);

  private static final EnumSet<ProcessInstanceIntent> TERMINATABLE_STATES =
      EnumSet.of(ELEMENT_ACTIVATING, ELEMENT_ACTIVATED, ELEMENT_COMPLETING);

  private static final Map<ProcessInstanceIntent, Set<ProcessInstanceIntent>> TRANSITION_RULES =
      new EnumMap<>(ProcessInstanceIntent.class);

  static {
    TRANSITION_RULES.put(ELEMENT_ACTIVATING, EnumSet.of(ELEMENT_ACTIVATED, ELEMENT_TERMINATING));
    TRANSITION_RULES.put(ELEMENT_ACTIVATED, EnumSet.of(ELEMENT_COMPLETING, ELEMENT_TERMINATING));
    TRANSITION_RULES.put(ELEMENT_COMPLETING, EnumSet.of(ELEMENT_COMPLETED, ELEMENT_TERMINATING));
    TRANSITION_RULES.put(ELEMENT_TERMINATING, EnumSet.of(ELEMENT_TERMINATED));
    TRANSITION_RULES.put(ELEMENT_COMPLETED, EnumSet.of(SEQUENCE_FLOW_TAKEN));
    TRANSITION_RULES.put(ELEMENT_TERMINATED, Collections.emptySet());
    TRANSITION_RULES.put(SEQUENCE_FLOW_TAKEN, EnumSet.of(ELEMENT_ACTIVATING));
  }

  private ProcessInstanceLifecycle() {}

  public static boolean canTransition(
      final ProcessInstanceIntent from, final ProcessInstanceIntent to) {
    return TRANSITION_RULES.get(from).contains(to);
  }

  public static boolean isFinalState(final ProcessInstanceIntent state) {
    return FINAL_ELEMENT_INSTANCE_STATES.contains(state);
  }

  public static boolean isInitialState(final ProcessInstanceIntent state) {
    return state == ELEMENT_ACTIVATING;
  }

  public static boolean isElementInstanceState(final ProcessInstanceIntent state) {
    return ELEMENT_INSTANCE_STATES.contains(state);
  }

  public static boolean isTokenState(final ProcessInstanceIntent state) {
    return !isElementInstanceState(state);
  }

  public static boolean canTerminate(final ProcessInstanceIntent currentState) {
    return TERMINATABLE_STATES.contains(currentState);
  }

  public static boolean isActive(final ProcessInstanceIntent currentState) {
    return currentState == ELEMENT_ACTIVATED;
  }

  public static boolean isTerminating(final ProcessInstanceIntent currentState) {
    return currentState == ELEMENT_TERMINATING;
  }
}
