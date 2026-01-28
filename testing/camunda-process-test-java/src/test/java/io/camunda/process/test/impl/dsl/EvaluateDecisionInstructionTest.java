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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateDecisionCommandStep1.EvaluateDecisionCommandStep2;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.ImmutableDecisionDefinitionSelector;
import io.camunda.process.test.api.dsl.instructions.EvaluateDecisionInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableEvaluateDecisionInstruction;
import io.camunda.process.test.impl.dsl.instructions.EvaluateDecisionInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EvaluateDecisionInstructionTest {

  private static final String DECISION_DEFINITION_ID = "my-decision";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final EvaluateDecisionInstructionHandler instructionHandler =
      new EvaluateDecisionInstructionHandler();

  @Test
  void shouldEvaluateDecisionByDecisionDefinitionId() {
    // given
    final EvaluateDecisionInstruction instruction =
        ImmutableEvaluateDecisionInstruction.builder()
            .decisionDefinitionSelector(
                ImmutableDecisionDefinitionSelector.builder()
                    .decisionDefinitionId(DECISION_DEFINITION_ID)
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newEvaluateDecisionCommand();

    final EvaluateDecisionCommandStep2 mockCommand =
        camundaClient
            .newEvaluateDecisionCommand()
            .decisionId(DECISION_DEFINITION_ID)
            .variables(Collections.emptyMap());

    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldSetVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("input", 100);
    variables.put("region", "US");

    final EvaluateDecisionInstruction instruction =
        ImmutableEvaluateDecisionInstruction.builder()
            .decisionDefinitionSelector(
                ImmutableDecisionDefinitionSelector.builder()
                    .decisionDefinitionId(DECISION_DEFINITION_ID)
                    .build())
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newEvaluateDecisionCommand();

    final EvaluateDecisionCommandStep2 mockCommand =
        camundaClient
            .newEvaluateDecisionCommand()
            .decisionId(DECISION_DEFINITION_ID)
            .variables(variables);

    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldFailIfDecisionDefinitionIdIsNotSet() {
    // given
    final EvaluateDecisionInstruction instruction =
        ImmutableEvaluateDecisionInstruction.builder()
            .decisionDefinitionSelector(ImmutableDecisionDefinitionSelector.builder().build())
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required property: decisionDefinitionId");
  }
}
