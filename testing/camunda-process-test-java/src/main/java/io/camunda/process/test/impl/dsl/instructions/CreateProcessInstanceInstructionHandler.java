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
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.instructions.CreateProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.createProcessInstance.CreateProcessInstanceRuntimeInstruction;
import io.camunda.process.test.api.dsl.instructions.createProcessInstance.CreateProcessInstanceTerminateRuntimeInstruction;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;

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
