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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import io.camunda.process.test.api.dsl.ImmutableDecisionSelector;
import io.camunda.process.test.api.dsl.instructions.AssertDecisionInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertDecisionInstruction;
import io.camunda.process.test.impl.dsl.instructions.AssertDecisionInstructionHandler;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssertDecisionInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AssertionFacade assertionFacade;

  @Mock private DecisionInstanceFilter decisionInstanceFilter;
  @Captor private ArgumentCaptor<DecisionSelector> decisionSelectorCaptor;

  private final AssertDecisionInstructionHandler instructionHandler =
      new AssertDecisionInstructionHandler();

  @Test
  void shouldSelectDecisionByDefinitionId() {
    // given
    final AssertDecisionInstruction instruction =
        ImmutableAssertDecisionInstruction.builder()
            .decisionSelector(
                ImmutableDecisionSelector.builder().decisionDefinitionId("my-decision").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatDecision(decisionSelectorCaptor.capture());

    decisionSelectorCaptor.getValue().applyFilter(decisionInstanceFilter);
    verify(decisionInstanceFilter).decisionDefinitionId("my-decision");

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldSelectDecisionByDefinitionName() {
    // given
    final AssertDecisionInstruction instruction =
        ImmutableAssertDecisionInstruction.builder()
            .decisionSelector(
                ImmutableDecisionSelector.builder()
                    .decisionDefinitionName("My Decision")
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatDecision(decisionSelectorCaptor.capture());

    decisionSelectorCaptor.getValue().applyFilter(decisionInstanceFilter);
    verify(decisionInstanceFilter).decisionDefinitionName("My Decision");

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldAssertOutput() {
    // given
    final AssertDecisionInstruction instruction =
        ImmutableAssertDecisionInstruction.builder()
            .decisionSelector(
                ImmutableDecisionSelector.builder().decisionDefinitionId("my-decision").build())
            .output("valid")
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatDecision(any());
    verify(assertionFacade.assertThatDecision(any())).hasOutput("valid");

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldAssertMatchedRules() {
    // given
    final AssertDecisionInstruction instruction =
        ImmutableAssertDecisionInstruction.builder()
            .decisionSelector(
                ImmutableDecisionSelector.builder().decisionDefinitionId("my-decision").build())
            .matchedRules(Arrays.asList(1, 3, 5))
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatDecision(any());
    verify(assertionFacade.assertThatDecision(any())).hasMatchedRules(1, 3, 5);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldAssertNotMatchedRules() {
    // given
    final AssertDecisionInstruction instruction =
        ImmutableAssertDecisionInstruction.builder()
            .decisionSelector(
                ImmutableDecisionSelector.builder().decisionDefinitionId("my-decision").build())
            .notMatchedRules(Arrays.asList(2, 4))
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatDecision(any());
    verify(assertionFacade.assertThatDecision(any())).hasNotMatchedRules(2, 4);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldAssertNoMatchedRules() {
    // given
    final AssertDecisionInstruction instruction =
        ImmutableAssertDecisionInstruction.builder()
            .decisionSelector(
                ImmutableDecisionSelector.builder().decisionDefinitionId("my-decision").build())
            .noMatchedRules(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatDecision(any());
    verify(assertionFacade.assertThatDecision(any())).hasNoMatchedRules();

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCombineMultipleAssertions() {
    // given
    final AssertDecisionInstruction instruction =
        ImmutableAssertDecisionInstruction.builder()
            .decisionSelector(
                ImmutableDecisionSelector.builder().decisionDefinitionId("my-decision").build())
            .output("valid")
            .matchedRules(Arrays.asList(1, 3))
            .notMatchedRules(Arrays.asList(2, 4))
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    final DecisionInstanceAssert decisionAssert = verify(assertionFacade).assertThatDecision(any());
    verify(decisionAssert).hasOutput("valid");
    verify(decisionAssert).hasMatchedRules(1, 3);
    verify(decisionAssert).hasNotMatchedRules(2, 4);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }
}
