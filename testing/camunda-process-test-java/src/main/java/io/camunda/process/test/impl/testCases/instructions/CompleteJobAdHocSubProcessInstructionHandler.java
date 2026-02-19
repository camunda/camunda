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
import io.camunda.process.test.api.testCases.instructions.CompleteJobAdHocSubProcessInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class CompleteJobAdHocSubProcessInstructionHandler
    implements TestCaseInstructionHandler<CompleteJobAdHocSubProcessInstruction> {

  @Override
  public void execute(
      final CompleteJobAdHocSubProcessInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final JobSelector jobSelector =
        InstructionSelectorFactory.buildJobSelector(instruction.getJobSelector());

    context.completeJobOfAdHocSubProcess(
        jobSelector,
        instruction.getVariables(),
        result -> {
          // Activate elements
          instruction
              .getActivateElements()
              .forEach(
                  element ->
                      result
                          .activateElement(element.getElementId())
                          .variables(element.getVariables()));

          // Set completion flags
          if (instruction.getCancelRemainingInstances()) {
            result.cancelRemainingInstances(true);
          }

          if (instruction.getCompletionConditionFulfilled()) {
            result.completionConditionFulfilled(true);
          }
        });
  }

  @Override
  public Class<CompleteJobAdHocSubProcessInstruction> getInstructionType() {
    return CompleteJobAdHocSubProcessInstruction.class;
  }
}
