/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

import java.util.EnumSet;
import java.util.Set;

public enum ProcessInstanceEngineIntent implements ProcessInstanceRelatedEngineIntent {
  CANCEL((short) 0, false),

  SEQUENCE_FLOW_TAKEN((short) 1),

  ELEMENT_ACTIVATING((short) 2),
  ELEMENT_ACTIVATED((short) 3),
  ELEMENT_COMPLETING((short) 4),
  ELEMENT_COMPLETED((short) 5),
  ELEMENT_TERMINATING((short) 6),
  ELEMENT_TERMINATED((short) 7),
  SEQUENCE_FLOW_DELETED((short) 15),

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

  /**
   * Represents the intent that triggers the continuation of a previously started termination of a
   * BPMN element.
   *
   * <p>This command is typically used after a pause in the termination flow - for example, when a
   * `canceling` task listener was triggered - to resume and finalize the termination of the user
   * task element.
   */
  CONTINUE_TERMINATING_ELEMENT((short) 14);

  private static final Set<ProcessInstanceEngineIntent> PROCESS_INSTANCE_COMMANDS = EnumSet.of(CANCEL);
  private static final Set<ProcessInstanceEngineIntent> BPMN_ELEMENT_COMMANDS =
      EnumSet.of(
          ACTIVATE_ELEMENT,
          COMPLETE_ELEMENT,
          TERMINATE_ELEMENT,
          COMPLETE_EXECUTION_LISTENER,
          CONTINUE_TERMINATING_ELEMENT);

  private final short value;
  private final boolean shouldBanInstance;

  ProcessInstanceEngineIntent(final short value) {
    this(value, true);
  }

  ProcessInstanceEngineIntent(final short value, final boolean shouldBanInstance) {
    this.value = value;
    this.shouldBanInstance = shouldBanInstance;
  }

  public short getIntent() {
    return value;
  }

  public static EngineIntent from(final short value) {
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
        return CONTINUE_TERMINATING_ELEMENT;
      case 15:
        return SEQUENCE_FLOW_DELETED;
      default:
        return EngineIntent.UNKNOWN;
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
      case SEQUENCE_FLOW_DELETED:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }

  public static boolean isProcessInstanceCommand(final ProcessInstanceEngineIntent intent) {
    return PROCESS_INSTANCE_COMMANDS.contains(intent);
  }

  public static boolean isBpmnElementCommand(final ProcessInstanceEngineIntent intent) {
    return BPMN_ELEMENT_COMMANDS.contains(intent);
  }

  public static boolean isBpmnElementEvent(final ProcessInstanceEngineIntent intent) {
    return !isProcessInstanceCommand(intent) && !isBpmnElementCommand(intent);
  }
}
