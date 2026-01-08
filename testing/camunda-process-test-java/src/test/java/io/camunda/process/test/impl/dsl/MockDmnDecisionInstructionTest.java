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
import io.camunda.process.test.api.dsl.instructions.ImmutableMockDmnDecisionInstruction;
import io.camunda.process.test.api.dsl.instructions.MockDmnDecisionInstruction;
import io.camunda.process.test.impl.dsl.instructions.MockDmnDecisionInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockDmnDecisionInstructionTest {

  private static final String DECISION_ID = "credit-check-decision";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final MockDmnDecisionInstructionHandler instructionHandler =
      new MockDmnDecisionInstructionHandler();

  @Test
  void shouldMockDmnDecisionWithoutVariables() {
    // given
    final MockDmnDecisionInstruction instruction =
        ImmutableMockDmnDecisionInstruction.builder().decisionDefinitionId(DECISION_ID).build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockDmnDecision(DECISION_ID, Collections.emptyMap());

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldMockDmnDecisionWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("approved", true);
    variables.put("score", 750);

    final MockDmnDecisionInstruction instruction =
        ImmutableMockDmnDecisionInstruction.builder()
            .decisionDefinitionId(DECISION_ID)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockDmnDecision(DECISION_ID, variables);

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }
}
