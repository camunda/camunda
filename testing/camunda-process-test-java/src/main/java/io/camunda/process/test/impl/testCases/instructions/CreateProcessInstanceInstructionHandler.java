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
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.instructions.CreateProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.createProcessInstance.CreateProcessInstanceRuntimeInstruction;
import io.camunda.process.test.api.testCases.instructions.createProcessInstance.CreateProcessInstanceTerminateRuntimeInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.CreatedProcessInstanceRegistry;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class CreateProcessInstanceInstructionHandler
    implements TestCaseInstructionHandler<CreateProcessInstanceInstruction> {

  private final CreatedProcessInstanceRegistry createdProcessInstances;

  public CreateProcessInstanceInstructionHandler(
      final CreatedProcessInstanceRegistry createdProcessInstances) {
    this.createdProcessInstances = createdProcessInstances;
  }

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

    final boolean isolated =
        instruction.getReserveJobs()
            || instruction.getStubCallActivities()
            || instruction.getHoldTimers();

    if (instruction.getReserveJobs()) {
      command.reserveJobs(context.getJobReservationToken());
    }
    if (instruction.getStubCallActivities()) {
      command.stubCallActivities(true);
    }
    if (instruction.getHoldTimers()) {
      command.holdTimers(true);
    }

    final ProcessInstanceEvent processInstance = command.send().join();

    // an isolated instance waits on jobs no worker takes, or on timers that never fire, so it
    // parks until the test ends it
    createdProcessInstances.register(
        processDefinitionId,
        processInstance.getProcessInstanceKey(),
        instruction.getHoldTimers(),
        isolated);
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
