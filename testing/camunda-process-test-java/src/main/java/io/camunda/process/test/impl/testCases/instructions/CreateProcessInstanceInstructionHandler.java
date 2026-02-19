/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.instructions.CreateProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.createProcessInstance.CreateProcessInstanceRuntimeInstruction;
import io.camunda.process.test.api.testCases.instructions.createProcessInstance.CreateProcessInstanceTerminateRuntimeInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class CreateProcessInstanceInstructionHandler
    implements TestCaseInstructionHandler<CreateProcessInstanceInstruction> {

  @Override
  public void execute(
      final CreateProcessInstanceInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final String processDefinitionId =
        instruction
            .getProcessDefinitionSelector()
            .getProcessDefinitionId()
            .orElseThrow(
                () ->
                    new IllegalArgumentException("Missing required property: processDefinitionId"));

    final CreateProcessInstanceCommandStep3 command =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processDefinitionId)
            .latestVersion()
            .variables(instruction.getVariables());

    instruction
        .getStartInstructions()
        .forEach(startInstruction -> command.startBeforeElement(startInstruction.getElementId()));

    instruction
        .getRuntimeInstructions()
        .forEach(runtimeInstruction -> applyRuntimeInstruction(runtimeInstruction, command));

    command.send().join();
  }

  @Override
  public Class<CreateProcessInstanceInstruction> getInstructionType() {
    return CreateProcessInstanceInstruction.class;
  }

  private static void applyRuntimeInstruction(
      final CreateProcessInstanceRuntimeInstruction runtimeInstruction,
      final CreateProcessInstanceCommandStep3 command) {

    if (runtimeInstruction instanceof CreateProcessInstanceTerminateRuntimeInstruction) {
      final CreateProcessInstanceTerminateRuntimeInstruction terminateInstruction =
          (CreateProcessInstanceTerminateRuntimeInstruction) runtimeInstruction;
      command.terminateAfterElement(terminateInstruction.getAfterElementId());

    } else {
      throw new IllegalArgumentException(
          "Unsupported runtime instruction: " + runtimeInstruction.getClass().getName());
    }
  }
}
