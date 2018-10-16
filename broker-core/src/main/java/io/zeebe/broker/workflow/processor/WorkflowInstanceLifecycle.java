/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.processor;

import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.EnumSet;

/**
 * We have two types of elements:
 *
 * <ul>
 *   <li>Those that simply <i>happen</i>, i.e. that are represented by a single event (e.g. sequence
 *       flow, none start event, ..)
 *   <li>Those that are <i>stateful</i> at runtime, i.e. that have a lifecycle and are represented
 *       as instances in the index (e.g. service task, sub process, ..)
 */
public class WorkflowInstanceLifecycle {

  public static final EnumSet<WorkflowInstanceIntent> ELEMENT_INSTANCE_STATES =
      EnumSet.of(
          WorkflowInstanceIntent.ELEMENT_READY,
          WorkflowInstanceIntent.ELEMENT_ACTIVATED,
          WorkflowInstanceIntent.ELEMENT_COMPLETING,
          WorkflowInstanceIntent.ELEMENT_COMPLETED,
          WorkflowInstanceIntent.ELEMENT_TERMINATING,
          WorkflowInstanceIntent.ELEMENT_TERMINATED);

  public static final EnumSet<WorkflowInstanceIntent> FINAL_ELEMENT_INSTANCE_STATES =
      EnumSet.of(
          WorkflowInstanceIntent.ELEMENT_COMPLETED, WorkflowInstanceIntent.ELEMENT_TERMINATED);

  public static final EnumSet<WorkflowInstanceIntent> TERMINATABLE_STATES =
      EnumSet.of(
          WorkflowInstanceIntent.ELEMENT_READY,
          WorkflowInstanceIntent.ELEMENT_ACTIVATED,
          WorkflowInstanceIntent.ELEMENT_COMPLETING);

  public static boolean isFinalState(WorkflowInstanceIntent state) {
    return FINAL_ELEMENT_INSTANCE_STATES.contains(state);
  }

  public static boolean isInitialState(WorkflowInstanceIntent state) {
    return state == WorkflowInstanceIntent.ELEMENT_READY;
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
}
