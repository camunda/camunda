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
package io.camunda.process.test.impl.dsl.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.dsl.instructions.AssertUserTaskInstruction;
import io.camunda.process.test.api.dsl.instructions.assertUserTask.UserTaskState;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;

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
