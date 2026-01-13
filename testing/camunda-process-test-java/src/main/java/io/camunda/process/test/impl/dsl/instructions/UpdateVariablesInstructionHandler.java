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
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.UpdateVariablesInstruction;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;
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

    final Optional<io.camunda.process.test.api.dsl.ElementSelector> dslElementSelector =
        instruction.getElementSelector();

    if (dslElementSelector.isPresent()) {
      // Local variables: update using element selector
      final ElementSelector elementSelector =
          InstructionSelectorFactory.buildElementSelector(dslElementSelector.get());
      context.updateLocalVariables(
          processInstanceSelector, elementSelector, instruction.getVariables());
    } else {
      // Global variables: update at process instance level
      context.updateVariables(processInstanceSelector, instruction.getVariables());
    }
  }

  @Override
  public Class<UpdateVariablesInstruction> getInstructionType() {
    return UpdateVariablesInstruction.class;
  }
}
