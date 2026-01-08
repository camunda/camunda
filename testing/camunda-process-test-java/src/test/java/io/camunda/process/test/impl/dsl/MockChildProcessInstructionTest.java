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
package io.camunda.process.test.impl.dsl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.instructions.ImmutableMockChildProcessInstruction;
import io.camunda.process.test.api.dsl.instructions.MockChildProcessInstruction;
import io.camunda.process.test.impl.dsl.instructions.MockChildProcessInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockChildProcessInstructionTest {

  private static final String CHILD_PROCESS_ID = "child-process";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final MockChildProcessInstructionHandler instructionHandler =
      new MockChildProcessInstructionHandler();

  @Test
  void shouldMockChildProcessWithoutVariables() {
    // given
    final MockChildProcessInstruction instruction =
        ImmutableMockChildProcessInstruction.builder()
            .processDefinitionId(CHILD_PROCESS_ID)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockChildProcess(CHILD_PROCESS_ID, Collections.emptyMap());

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldMockChildProcessWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("amount", 100.0);
    variables.put("currency", "USD");

    final MockChildProcessInstruction instruction =
        ImmutableMockChildProcessInstruction.builder()
            .processDefinitionId(CHILD_PROCESS_ID)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockChildProcess(CHILD_PROCESS_ID, variables);

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }
}
