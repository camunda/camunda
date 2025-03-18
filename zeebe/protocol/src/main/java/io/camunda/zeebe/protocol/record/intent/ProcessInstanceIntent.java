/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol.record.intent;

import java.util.EnumSet;
import java.util.Set;

public enum ProcessInstanceIntent implements ProcessInstanceRelatedIntent {
  CANCEL((short) 0, false),

  SEQUENCE_FLOW_TAKEN((short) 1),

  ELEMENT_ACTIVATING((short) 2),
  ELEMENT_ACTIVATED((short) 3),
  ELEMENT_COMPLETING((short) 4),
  ELEMENT_COMPLETED((short) 5),
  ELEMENT_TERMINATING((short) 6),
  ELEMENT_TERMINATED((short) 7),

  ACTIVATE_ELEMENT((short) 8),
  COMPLETE_ELEMENT((short) 9),
  TERMINATE_ELEMENT((short) 10),

  ELEMENT_MIGRATED((short) 11),

  /**
   * Represents the intent that signals about the completion of execution listener job, allowing
   * either the creation of the next execution listener job or the finalization of the process
   * element activation (for `start` listeners) or completion (for `end` listeners) phases.
   *
   * <p>Until this intent is written, the execution of the process element is paused, ensuring that
   * all operations defined by the listener are fully executed before proceeding with the element's
   * activation or completion.
   */
  COMPLETE_EXECUTION_LISTENER((short) 12),
  /** Represents the intent signaling the migration of an ancestor element instance. */
  ANCESTOR_MIGRATED((short) 13),
  CANCEL_BATCH_OPERATION((short) 14);

  private static final Set<ProcessInstanceIntent> PROCESS_INSTANCE_COMMANDS =
      EnumSet.of(CANCEL, CANCEL_BATCH_OPERATION);
  private static final Set<ProcessInstanceIntent> BPMN_ELEMENT_COMMANDS =
      EnumSet.of(
          ACTIVATE_ELEMENT, COMPLETE_ELEMENT, TERMINATE_ELEMENT, COMPLETE_EXECUTION_LISTENER);

  private final short value;
  private final boolean shouldBanInstance;

  ProcessInstanceIntent(final short value) {
    this(value, true);
  }

  ProcessInstanceIntent(final short value, final boolean shouldBanInstance) {
    this.value = value;
    this.shouldBanInstance = shouldBanInstance;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CANCEL;
      case 1:
        return SEQUENCE_FLOW_TAKEN;
      case 2:
        return ELEMENT_ACTIVATING;
      case 3:
        return ELEMENT_ACTIVATED;
      case 4:
        return ELEMENT_COMPLETING;
      case 5:
        return ELEMENT_COMPLETED;
      case 6:
        return ELEMENT_TERMINATING;
      case 7:
        return ELEMENT_TERMINATED;
      case 8:
        return ACTIVATE_ELEMENT;
      case 9:
        return COMPLETE_ELEMENT;
      case 10:
        return TERMINATE_ELEMENT;
      case 11:
        return ELEMENT_MIGRATED;
      case 12:
        return COMPLETE_EXECUTION_LISTENER;
      case 13:
        return ANCESTOR_MIGRATED;
      case 14:
        return CANCEL_BATCH_OPERATION;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case SEQUENCE_FLOW_TAKEN:
      case ELEMENT_ACTIVATING:
      case ELEMENT_ACTIVATED:
      case ELEMENT_COMPLETING:
      case ELEMENT_COMPLETED:
      case ELEMENT_TERMINATING:
      case ELEMENT_TERMINATED:
      case ELEMENT_MIGRATED:
      case ANCESTOR_MIGRATED:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }

  public static boolean isProcessInstanceCommand(final ProcessInstanceIntent intent) {
    return PROCESS_INSTANCE_COMMANDS.contains(intent);
  }

  public static boolean isBpmnElementCommand(final ProcessInstanceIntent intent) {
    return BPMN_ELEMENT_COMMANDS.contains(intent);
  }

  public static boolean isBpmnElementEvent(final ProcessInstanceIntent intent) {
    return !isProcessInstanceCommand(intent) && !isBpmnElementCommand(intent);
  }
}
