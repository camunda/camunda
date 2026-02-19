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
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.testCases.instructions.CompleteJobUserTaskListenerInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

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
