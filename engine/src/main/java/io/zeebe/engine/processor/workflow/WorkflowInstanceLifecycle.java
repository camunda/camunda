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
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class WorkflowInstanceLifecycle {

  public static final EnumSet<WorkflowInstanceIntent> ELEMENT_INSTANCE_STATES =
      EnumSet.of(
          ELEMENT_ACTIVATING,
          ELEMENT_ACTIVATED,
          ELEMENT_COMPLETING,
          ELEMENT_COMPLETED,
          ELEMENT_TERMINATING,
          ELEMENT_TERMINATED);

  public static final EnumSet<WorkflowInstanceIntent> FINAL_ELEMENT_INSTANCE_STATES =
      EnumSet.of(ELEMENT_COMPLETED, ELEMENT_TERMINATED);

  public static final EnumSet<WorkflowInstanceIntent> TERMINATABLE_STATES =
      EnumSet.of(ELEMENT_ACTIVATING, ELEMENT_ACTIVATED, ELEMENT_COMPLETING);

  public static final Map<WorkflowInstanceIntent, Set<WorkflowInstanceIntent>> TRANSITION_RULES =
      new EnumMap<>(WorkflowInstanceIntent.class);

  static {
    TRANSITION_RULES.put(ELEMENT_ACTIVATING, EnumSet.of(ELEMENT_ACTIVATED, ELEMENT_TERMINATING));
    TRANSITION_RULES.put(ELEMENT_ACTIVATED, EnumSet.of(ELEMENT_COMPLETING, ELEMENT_TERMINATING));
    TRANSITION_RULES.put(ELEMENT_COMPLETING, EnumSet.of(ELEMENT_COMPLETED, ELEMENT_TERMINATING));
    TRANSITION_RULES.put(ELEMENT_TERMINATING, EnumSet.of(ELEMENT_TERMINATED));
    TRANSITION_RULES.put(ELEMENT_COMPLETED, Collections.emptySet());
    TRANSITION_RULES.put(ELEMENT_TERMINATED, Collections.emptySet());
  }

  public static boolean canTransition(WorkflowInstanceIntent from, WorkflowInstanceIntent to) {
    return TRANSITION_RULES.get(from).contains(to);
  }

  public static boolean isFinalState(WorkflowInstanceIntent state) {
    return FINAL_ELEMENT_INSTANCE_STATES.contains(state);
  }

  public static boolean isInitialState(WorkflowInstanceIntent state) {
    return state == ELEMENT_ACTIVATING;
  }

  public static boolean isElementInstanceState(WorkflowInstanceIntent state) {
    return ELEMENT_INSTANCE_STATES.contains(state);
  }

  public static boolean isTokenState(WorkflowInstanceIntent state) {
    return !isElementInstanceState(state);
  }

  public static boolean canTerminate(WorkflowInstanceIntent currentState) {
    return TERMINATABLE_STATES.contains(currentState);
  }

  public static boolean isActive(WorkflowInstanceIntent currentState) {
    return currentState == ELEMENT_ACTIVATED;
  }

  public static boolean isTerminating(WorkflowInstanceIntent currentState) {
    return currentState == ELEMENT_TERMINATING;
  }
}
