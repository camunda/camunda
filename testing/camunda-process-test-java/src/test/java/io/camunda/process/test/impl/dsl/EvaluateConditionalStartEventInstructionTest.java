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
import io.camunda.process.test.api.dsl.instructions.EvaluateConditionalStartEventInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableEvaluateConditionalStartEventInstruction;
import io.camunda.process.test.impl.dsl.instructions.EvaluateConditionalStartEventInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EvaluateConditionalStartEventInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final EvaluateConditionalStartEventInstructionHandler instructionHandler =
      new EvaluateConditionalStartEventInstructionHandler();

  @Test
  void shouldEvaluateConditionalStartEventWithoutVariables() {
    // given
    final EvaluateConditionalStartEventInstruction instruction =
        ImmutableEvaluateConditionalStartEventInstruction.builder().build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newEvaluateConditionalCommand();
    verify(camundaClient.newEvaluateConditionalCommand()).variables(Collections.emptyMap());
    verify(camundaClient.newEvaluateConditionalCommand().variables(Collections.emptyMap()))
        .send();

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldSetVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 1);
    variables.put("status", "active");

    final EvaluateConditionalStartEventInstruction instruction =
        ImmutableEvaluateConditionalStartEventInstruction.builder()
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newEvaluateConditionalCommand();
    verify(camundaClient.newEvaluateConditionalCommand()).variables(variables);
    verify(camundaClient.newEvaluateConditionalCommand().variables(variables)).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }
}
