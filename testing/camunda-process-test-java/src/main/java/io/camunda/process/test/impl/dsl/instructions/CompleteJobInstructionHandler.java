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
import io.camunda.process.test.api.assertions.JobSelectors;
import io.camunda.process.test.api.dsl.instructions.CompleteJobInstruction;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;

public class CompleteJobInstructionHandler
    implements TestCaseInstructionHandler<CompleteJobInstruction> {

  @Override
  public void execute(
      final CompleteJobInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final JobSelector jobSelector = buildJobSelector(instruction);

    if (instruction.isWithExampleData()) {
      context.completeJobWithExampleData(jobSelector);
    } else {
      context.completeJob(jobSelector, instruction.getVariables());
    }
  }

  @Override
  public Class<CompleteJobInstruction> getInstructionType() {
    return CompleteJobInstruction.class;
  }

  private JobSelector buildJobSelector(final CompleteJobInstruction instruction) {
    JobSelector selector = null;

    if (instruction.getJobSelector().getJobType().isPresent()) {
      final String jobType = instruction.getJobSelector().getJobType().get();
      selector = JobSelectors.byJobType(jobType);
    }

    if (instruction.getJobSelector().getElementId().isPresent()) {
      final String elementId = instruction.getJobSelector().getElementId().get();
      final JobSelector elementIdSelector = JobSelectors.byElementId(elementId);
      selector = selector == null ? elementIdSelector : selector.and(elementIdSelector);
    }

    if (instruction.getJobSelector().getProcessDefinitionId().isPresent()) {
      final String processDefinitionId =
          instruction.getJobSelector().getProcessDefinitionId().get();
      final JobSelector processDefSelector =
          JobSelectors.byProcessDefinitionId(processDefinitionId);
      selector = selector == null ? processDefSelector : selector.and(processDefSelector);
    }

    if (selector == null) {
      throw new IllegalArgumentException(
          "JobSelector must have at least one property: jobType, elementId, or processDefinitionId");
    }

    return selector;
  }
}
