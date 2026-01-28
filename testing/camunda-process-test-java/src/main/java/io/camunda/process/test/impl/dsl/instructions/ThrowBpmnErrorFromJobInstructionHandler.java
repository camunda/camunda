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
import io.camunda.process.test.api.dsl.instructions.ThrowBpmnErrorFromJobInstruction;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;

public class ThrowBpmnErrorFromJobInstructionHandler
    implements TestCaseInstructionHandler<ThrowBpmnErrorFromJobInstruction> {

  @Override
  public void execute(
      final ThrowBpmnErrorFromJobInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final JobSelector jobSelector =
        InstructionSelectorFactory.buildJobSelector(instruction.getJobSelector());

    if (instruction.getErrorMessage().isPresent()) {
      context.throwBpmnErrorFromJob(
          jobSelector,
          instruction.getErrorCode(),
          instruction.getErrorMessage().get(),
          instruction.getVariables());
    } else {
      context.throwBpmnErrorFromJob(
          jobSelector, instruction.getErrorCode(), instruction.getVariables());
    }
  }

  @Override
  public Class<ThrowBpmnErrorFromJobInstruction> getInstructionType() {
    return ThrowBpmnErrorFromJobInstruction.class;
  }
}
