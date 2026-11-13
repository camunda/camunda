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
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.process.test.api.testCases.instructions.MockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import io.camunda.process.test.impl.testCases.TestRunContext;

public class MockJobWorkerCompleteJobInstructionHandler
    implements TestCaseInstructionHandler<MockJobWorkerCompleteJobInstruction> {

  private final TestRunContext runContext;

  public MockJobWorkerCompleteJobInstructionHandler(final TestRunContext runContext) {
    this.runContext = runContext;
  }

  @Override
  public void execute(
      final MockJobWorkerCompleteJobInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final JobWorkerMockBuilder mockBuilder = context.mockJobWorker(instruction.getJobType());
    instruction
        .getJobReservationToken()
        .map(runContext::resolveJobReservationToken)
        .ifPresent(mockBuilder::withJobReservationToken);

    if (instruction.getUseExampleData()) {
      mockBuilder.thenCompleteWithExampleData();
    } else {
      mockBuilder.thenComplete(instruction.getVariables());
    }
  }

  @Override
  public Class<MockJobWorkerCompleteJobInstruction> getInstructionType() {
    return MockJobWorkerCompleteJobInstruction.class;
  }
}
