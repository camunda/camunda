/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETING;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_TERMINATING;

import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.util.Lists;

public class LifecycleAssert
    extends AbstractListAssert<
        LifecycleAssert,
        List<WorkflowInstanceIntent>,
        WorkflowInstanceIntent,
        ObjectAssert<WorkflowInstanceIntent>> {

  /*
   * Contains all valid lifecycle transitions
   */
  private static final EnumMap<WorkflowInstanceIntent, EnumSet<WorkflowInstanceIntent>>
      ELEMENT_LIFECYCLE;
  private static final EnumSet<WorkflowInstanceIntent> FINAL_STATES =
      EnumSet.of(ELEMENT_COMPLETED, ELEMENT_TERMINATED);
  private static final WorkflowInstanceIntent INITIAL_STATE = ELEMENT_ACTIVATING;

  static {
    ELEMENT_LIFECYCLE = new EnumMap<>(WorkflowInstanceIntent.class);

    ELEMENT_LIFECYCLE.put(ELEMENT_ACTIVATING, EnumSet.of(ELEMENT_ACTIVATED, ELEMENT_TERMINATING));
    ELEMENT_LIFECYCLE.put(ELEMENT_ACTIVATED, EnumSet.of(ELEMENT_COMPLETING, ELEMENT_TERMINATING));
    ELEMENT_LIFECYCLE.put(ELEMENT_COMPLETING, EnumSet.of(ELEMENT_COMPLETED, ELEMENT_TERMINATING));
    ELEMENT_LIFECYCLE.put(ELEMENT_COMPLETED, EnumSet.noneOf(WorkflowInstanceIntent.class));
    ELEMENT_LIFECYCLE.put(ELEMENT_TERMINATING, EnumSet.of(ELEMENT_TERMINATED));
    ELEMENT_LIFECYCLE.put(ELEMENT_TERMINATED, EnumSet.noneOf(WorkflowInstanceIntent.class));
  }

  public LifecycleAssert(List<WorkflowInstanceIntent> actual) {
    super(actual, LifecycleAssert.class);
  }

  /**
   * Also checks that initial state and final state match the lifecyle's initial and final states
   */
  public LifecycleAssert compliesWithCompleteLifecycle() {
    if (actual.isEmpty()) {
      return this;
    }

    final WorkflowInstanceIntent initialState = actual.get(0);
    if (INITIAL_STATE != initialState) {
      failWithMessage("Wrong initial state. Expected %s, was %s.", INITIAL_STATE, initialState);
    }

    compliesWithLifecycle();

    final WorkflowInstanceIntent finalState = actual.get(actual.size() - 1);

    if (!FINAL_STATES.contains(finalState)) {
      failWithMessage("Wrong final state. Expected one of %s, was %s.", FINAL_STATES, finalState);
    }

    return this;
  }

  public LifecycleAssert compliesWithLifecycle() {
    for (int i = 0; i < actual.size() - 1; i++) {
      final WorkflowInstanceIntent from = actual.get(i);
      final WorkflowInstanceIntent to = actual.get(i + 1);

      if (!ELEMENT_LIFECYCLE.get(from).contains(to)) {
        failWithMessage(
            "Element transition not allowed by lifecycle: %s -> %s.\n"
                + " Actual transitions were: %s",
            from, to, actual);
      }
    }

    return this;
  }

  @Override
  protected ObjectAssert<WorkflowInstanceIntent> toAssert(
      WorkflowInstanceIntent value, String description) {
    return new ObjectAssert<>(value).describedAs(description);
  }

  @Override
  protected LifecycleAssert newAbstractIterableAssert(
      Iterable<? extends WorkflowInstanceIntent> iterable) {
    return new LifecycleAssert(Lists.newArrayList(iterable));
  }

  public static LifecycleAssert assertThat(List<WorkflowInstanceIntent> trajectory) {
    return new LifecycleAssert(trajectory);
  }
}
