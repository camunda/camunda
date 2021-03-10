/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn;

import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETING;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static io.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATING;

import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.util.Lists;

public final class LifecycleAssert
    extends AbstractListAssert<
        LifecycleAssert,
        List<ProcessInstanceIntent>,
        ProcessInstanceIntent,
        ObjectAssert<ProcessInstanceIntent>> {

  /*
   * Contains all valid lifecycle transitions
   */
  private static final EnumMap<ProcessInstanceIntent, EnumSet<ProcessInstanceIntent>>
      ELEMENT_LIFECYCLE;
  private static final EnumSet<ProcessInstanceIntent> FINAL_STATES =
      EnumSet.of(ELEMENT_COMPLETED, ELEMENT_TERMINATED);
  private static final ProcessInstanceIntent INITIAL_STATE = ELEMENT_ACTIVATING;

  static {
    ELEMENT_LIFECYCLE = new EnumMap<>(ProcessInstanceIntent.class);

    ELEMENT_LIFECYCLE.put(ELEMENT_ACTIVATING, EnumSet.of(ELEMENT_ACTIVATED, ELEMENT_TERMINATING));
    ELEMENT_LIFECYCLE.put(ELEMENT_ACTIVATED, EnumSet.of(ELEMENT_COMPLETING, ELEMENT_TERMINATING));
    ELEMENT_LIFECYCLE.put(ELEMENT_COMPLETING, EnumSet.of(ELEMENT_COMPLETED, ELEMENT_TERMINATING));
    ELEMENT_LIFECYCLE.put(ELEMENT_COMPLETED, EnumSet.noneOf(ProcessInstanceIntent.class));
    ELEMENT_LIFECYCLE.put(ELEMENT_TERMINATING, EnumSet.of(ELEMENT_TERMINATED));
    ELEMENT_LIFECYCLE.put(ELEMENT_TERMINATED, EnumSet.noneOf(ProcessInstanceIntent.class));
  }

  public LifecycleAssert(final List<ProcessInstanceIntent> actual) {
    super(actual, LifecycleAssert.class);
  }

  /**
   * Also checks that initial state and final state match the lifecyle's initial and final states
   */
  public LifecycleAssert compliesWithCompleteLifecycle() {
    if (actual.isEmpty()) {
      return this;
    }

    final ProcessInstanceIntent initialState = actual.get(0);
    if (INITIAL_STATE != initialState) {
      failWithMessage("Wrong initial state. Expected %s, was %s.", INITIAL_STATE, initialState);
    }

    compliesWithLifecycle();

    final ProcessInstanceIntent finalState = actual.get(actual.size() - 1);

    if (!FINAL_STATES.contains(finalState)) {
      failWithMessage("Wrong final state. Expected one of %s, was %s.", FINAL_STATES, finalState);
    }

    return this;
  }

  public LifecycleAssert compliesWithLifecycle() {
    for (int i = 0; i < actual.size() - 1; i++) {
      final ProcessInstanceIntent from = actual.get(i);
      final ProcessInstanceIntent to = actual.get(i + 1);

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
  protected ObjectAssert<ProcessInstanceIntent> toAssert(
      final ProcessInstanceIntent value, final String description) {
    return new ObjectAssert<>(value).describedAs(description);
  }

  @Override
  protected LifecycleAssert newAbstractIterableAssert(
      final Iterable<? extends ProcessInstanceIntent> iterable) {
    return new LifecycleAssert(Lists.newArrayList(iterable));
  }

  public static LifecycleAssert assertThat(final List<ProcessInstanceIntent> trajectory) {
    return new LifecycleAssert(trajectory);
  }
}
