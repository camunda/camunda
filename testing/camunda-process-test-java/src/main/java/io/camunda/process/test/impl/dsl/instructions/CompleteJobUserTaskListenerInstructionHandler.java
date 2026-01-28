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
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.dsl.instructions.CompleteJobUserTaskListenerInstruction;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;

public class CompleteJobUserTaskListenerInstructionHandler
    implements TestCaseInstructionHandler<CompleteJobUserTaskListenerInstruction> {

  @Override
  public void execute(
      final CompleteJobUserTaskListenerInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final JobSelector jobSelector =
        InstructionSelectorFactory.buildJobSelector(instruction.getJobSelector());

    context.completeJobOfUserTaskListener(
        jobSelector,
        result -> {
          // Apply denial if specified
          if (instruction.getDenied()) {
            result.deny(true);
            instruction.getDeniedReason().ifPresent(result::deniedReason);
            // If denied, no corrections are allowed
            return;
          }

          // Apply corrections if specified
          instruction
              .getCorrections()
              .ifPresent(
                  corrections -> {
                    corrections.getAssignee().ifPresent(result::correctAssignee);
                    corrections.getDueDate().ifPresent(result::correctDueDate);
                    corrections.getFollowUpDate().ifPresent(result::correctFollowUpDate);
                    if (!corrections.getCandidateUsers().isEmpty()) {
                      result.correctCandidateUsers(corrections.getCandidateUsers());
                    }
                    if (!corrections.getCandidateGroups().isEmpty()) {
                      result.correctCandidateGroups(corrections.getCandidateGroups());
                    }
                    corrections.getPriority().ifPresent(result::correctPriority);
                  });
        });
  }

  @Override
  public Class<CompleteJobUserTaskListenerInstruction> getInstructionType() {
    return CompleteJobUserTaskListenerInstruction.class;
  }
}
