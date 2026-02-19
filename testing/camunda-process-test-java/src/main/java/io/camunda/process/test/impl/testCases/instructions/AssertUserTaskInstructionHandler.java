/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.testCases.instructions.AssertUserTaskInstruction;
import io.camunda.process.test.api.testCases.instructions.assertUserTask.UserTaskState;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class AssertUserTaskInstructionHandler
    implements TestCaseInstructionHandler<AssertUserTaskInstruction> {

  @Override
  public void execute(
      final AssertUserTaskInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final UserTaskSelector userTaskSelector =
        InstructionSelectorFactory.buildUserTaskSelector(instruction.getUserTaskSelector());

    final UserTaskAssert userTaskAssert = assertionFacade.assertThatUserTask(userTaskSelector);

    instruction.getState().ifPresent(expectedState -> assertState(userTaskAssert, expectedState));

    instruction.getAssignee().ifPresent(userTaskAssert::hasAssignee);

    if (!instruction.getCandidateGroups().isEmpty()) {
      userTaskAssert.hasCandidateGroups(instruction.getCandidateGroups());
    }

    instruction.getPriority().ifPresent(userTaskAssert::hasPriority);

    instruction.getElementId().ifPresent(userTaskAssert::hasElementId);

    instruction.getName().ifPresent(userTaskAssert::hasName);

    instruction.getDueDate().ifPresent(userTaskAssert::hasDueDate);

    instruction.getFollowUpDate().ifPresent(userTaskAssert::hasFollowUpDate);
  }

  @Override
  public Class<AssertUserTaskInstruction> getInstructionType() {
    return AssertUserTaskInstruction.class;
  }

  private static void assertState(
      final UserTaskAssert userTaskAssert, final UserTaskState expectedState) {
    switch (expectedState) {
      case IS_CREATED:
        userTaskAssert.isCreated();
        break;
      case IS_COMPLETED:
        userTaskAssert.isCompleted();
        break;
      case IS_CANCELED:
        userTaskAssert.isCanceled();
        break;
      case IS_FAILED:
        userTaskAssert.isFailed();
        break;
      default:
        throw new IllegalArgumentException("Unsupported user task state: " + expectedState);
    }
  }
}
