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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.ImmutableElementSelector;
import io.camunda.process.test.api.dsl.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.ImmutableUpdateVariablesInstruction;
import io.camunda.process.test.api.dsl.instructions.UpdateVariablesInstruction;
import io.camunda.process.test.impl.dsl.instructions.UpdateVariablesInstructionHandler;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UpdateVariablesInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "my-process";
  private static final String ELEMENT_ID = "my-element";

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;
  @Mock private AssertionFacade assertionFacade;

  private final UpdateVariablesInstructionHandler instructionHandler =
      new UpdateVariablesInstructionHandler();

  @Test
  void shouldUpdateGlobalVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("status", "ready");
    variables.put("count", 42);

    final UpdateVariablesInstruction instruction =
        ImmutableUpdateVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).updateVariables(any(), eq(variables));
  }

  @Test
  void shouldUpdateLocalVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("localVar", "localValue");

    final UpdateVariablesInstruction instruction =
        ImmutableUpdateVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(
                ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).updateLocalVariables(any(), any(), eq(variables));
  }
}
