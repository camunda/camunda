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
import io.camunda.process.test.api.testCases.instructions.createProcessInstance.CreateProcessInstanceConfigureExecutionRuntimeInstruction;
import io.camunda.process.test.api.testCases.instructions.createProcessInstance.CreateProcessInstanceRuntimeInstruction;
import io.camunda.process.test.api.testCases.instructions.createProcessInstance.CreateProcessInstanceStubCallActivitiesRuntimeInstruction;
import io.camunda.process.test.api.testCases.instructions.createProcessInstance.CreateProcessInstanceTerminateRuntimeInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.CreatedProcessInstanceRegistry;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import io.camunda.process.test.impl.testCases.TestRunContext;
import java.util.List;
import java.util.Optional;

public class CreateProcessInstanceInstructionHandler
    implements TestCaseInstructionHandler<CreateProcessInstanceInstruction> {

  private final CreatedProcessInstanceRegistry createdProcessInstances;
  private final TestRunContext runContext;

  public CreateProcessInstanceInstructionHandler(
      final CreatedProcessInstanceRegistry createdProcessInstances,
      final TestRunContext runContext) {
    this.createdProcessInstances = createdProcessInstances;
    this.runContext = runContext;
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

    if (runContext.isEnabled()) {
      // stamp the reserved run-scope tag so the engine can isolate signal broadcast to this run
      command.tags(runContext.getRunScopeTag());
    }

    instruction
        .getStartInstructions()
        .forEach(startInstruction -> command.startBeforeElement(startInstruction.getElementId()));

    instruction
        .getRuntimeInstructions()
        .forEach(runtimeInstruction -> applyRuntimeInstruction(runtimeInstruction, command));

    final Optional<CreateProcessInstanceConfigureExecutionRuntimeInstruction> config =
        configureExecutionOf(instruction);

    final boolean reserveJobs =
        config
            .map(CreateProcessInstanceConfigureExecutionRuntimeInstruction::getReserveJobs)
            .orElse(false);
    final boolean holdTimers =
        config
            .map(CreateProcessInstanceConfigureExecutionRuntimeInstruction::getHoldTimers)
            .orElse(false);
    final List<String> stubbedCallActivityProcessIds = stubbedCallActivityProcessIdsOf(instruction);
    final boolean stubCallActivities = !stubbedCallActivityProcessIds.isEmpty();

    final boolean isolated = reserveJobs || stubCallActivities || holdTimers;

    if (reserveJobs) {
      command.reserveJobs(context.getJobReservationToken());
    }
    if (stubCallActivities) {
      command.stubCallActivities(stubbedCallActivityProcessIds.toArray(new String[0]));
    }
    if (holdTimers) {
      command.holdTimers(true);
    }

    final ProcessInstanceEvent processInstance = command.send().join();

    // an isolated instance waits on jobs no worker takes, or on timers that never fire, so it
    // parks until the test ends it
    createdProcessInstances.register(
        processDefinitionId, processInstance.getProcessInstanceKey(), holdTimers, isolated);
  }

  private static Optional<CreateProcessInstanceConfigureExecutionRuntimeInstruction>
      configureExecutionOf(final CreateProcessInstanceInstruction instruction) {
    return instruction.getRuntimeInstructions().stream()
        .filter(CreateProcessInstanceConfigureExecutionRuntimeInstruction.class::isInstance)
        .map(CreateProcessInstanceConfigureExecutionRuntimeInstruction.class::cast)
        .findFirst();
  }

  private static List<String> stubbedCallActivityProcessIdsOf(
      final CreateProcessInstanceInstruction instruction) {
    return instruction.getRuntimeInstructions().stream()
        .filter(CreateProcessInstanceStubCallActivitiesRuntimeInstruction.class::isInstance)
        .map(CreateProcessInstanceStubCallActivitiesRuntimeInstruction.class::cast)
        .map(CreateProcessInstanceStubCallActivitiesRuntimeInstruction::getProcessIds)
        .findFirst()
        .orElseGet(java.util.Collections::emptyList);
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

    } else if (runtimeInstruction
            instanceof CreateProcessInstanceConfigureExecutionRuntimeInstruction
        || runtimeInstruction
            instanceof CreateProcessInstanceStubCallActivitiesRuntimeInstruction) {
      // instance-wide switches are applied in execute(), not as a per-element command instruction

    } else {
      throw new IllegalArgumentException(
          "Unsupported runtime instruction: " + runtimeInstruction.getClass().getName());
    }
  }
}
