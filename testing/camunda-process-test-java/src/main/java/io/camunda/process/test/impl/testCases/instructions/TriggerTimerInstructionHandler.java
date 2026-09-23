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
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.instructions.TriggerTimerInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.CreatedProcessInstanceRegistry;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class TriggerTimerInstructionHandler
    implements TestCaseInstructionHandler<TriggerTimerInstruction> {

  private final CreatedProcessInstanceRegistry createdProcessInstances;

  public TriggerTimerInstructionHandler(
      final CreatedProcessInstanceRegistry createdProcessInstances) {
    this.createdProcessInstances = createdProcessInstances;
  }

  @Override
  public void execute(
      final TriggerTimerInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final String processDefinitionId =
        instruction
            .getProcessInstanceSelector()
            .getProcessDefinitionId()
            .orElseThrow(
                () ->
                    new IllegalArgumentException("Missing required property: processDefinitionId"));
    final String elementId =
        InstructionSelectorFactory.buildTimerElementId(instruction.getElementSelector());

    // resolved from the instances this test case created, not from a cluster query: a query also
    // matches an instance of an earlier test case or of a concurrent run against the same cluster
    final long processInstanceKey =
        createdProcessInstances.resolveHeldTimerProcessInstanceKey(processDefinitionId);

    context.triggerTimer(processInstanceKey, elementId);
  }

  @Override
  public Class<TriggerTimerInstruction> getInstructionType() {
    return TriggerTimerInstruction.class;
  }
}
