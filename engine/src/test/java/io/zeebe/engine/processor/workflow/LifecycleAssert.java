/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow;

import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETING;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_TERMINATING;

import io.zeebe.protocol.intent.WorkflowInstanceIntent;
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
