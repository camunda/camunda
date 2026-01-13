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
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.UpdateVariablesInstruction;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;
import java.util.List;
import java.util.Optional;

public class UpdateVariablesInstructionHandler
    implements TestCaseInstructionHandler<UpdateVariablesInstruction> {

  @Override
  public void execute(
      final UpdateVariablesInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final CamundaDataSource dataSource = new CamundaDataSource(camundaClient);

    // Find the process instance
    final List<ProcessInstance> processInstances =
        dataSource.findProcessInstances(processInstanceSelector::applyFilter);

    if (processInstances.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "No process instance found for selector [%s]",
              processInstanceSelector.describe()));
    }

    final ProcessInstance processInstance =
        processInstances.stream()
            .filter(processInstanceSelector::test)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format(
                            "No process instance found for selector [%s]",
                            processInstanceSelector.describe())));

    final long elementInstanceKey;

    // Determine the element instance key based on whether this is a global or local variable update
    final Optional<io.camunda.process.test.api.dsl.ElementSelector> dslElementSelector =
        instruction.getElementSelector();

    if (dslElementSelector.isPresent()) {
      // Local variables: find the element instance
      final ElementSelector elementSelector =
          InstructionSelectorFactory.buildElementSelector(dslElementSelector.get());

      final List<ElementInstance> elementInstances =
          dataSource.findElementInstances(
              filter -> {
                filter.processInstanceKey(processInstance.getProcessInstanceKey());
                elementSelector.applyFilter(filter);
              });

      final Optional<ElementInstance> elementInstance =
          elementInstances.stream().filter(elementSelector::test).findFirst();

      if (!elementInstance.isPresent()) {
        throw new IllegalArgumentException(
            String.format(
                "No element [%s] found for process instance [key: %s]",
                elementSelector.describe(), processInstance.getProcessInstanceKey()));
      }

      elementInstanceKey = elementInstance.get().getElementInstanceKey();

    } else {
      // Global variables: use the process instance key as the element instance key
      elementInstanceKey = processInstance.getProcessInstanceKey();
    }

    // Update the variables
    camundaClient
        .newSetVariablesCommand(elementInstanceKey)
        .variables(instruction.getVariables())
        .send()
        .join();
  }

  @Override
  public Class<UpdateVariablesInstruction> getInstructionType() {
    return UpdateVariablesInstruction.class;
  }
}
