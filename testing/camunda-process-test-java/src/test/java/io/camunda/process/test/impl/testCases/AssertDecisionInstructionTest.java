/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import io.camunda.process.test.api.testCases.ImmutableDecisionSelector;
import io.camunda.process.test.api.testCases.instructions.AssertDecisionInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertDecisionInstruction;
import io.camunda.process.test.impl.testCases.instructions.AssertDecisionInstructionHandler;
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
                ImmutableDecisionSelector.builder().decisionDefinitionName("My Decision").build())
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

    final DecisionInstanceAssert decisionInstanceAssert = assertionFacade.assertThatDecision(any());
    verify(decisionInstanceAssert).hasOutput("valid");

    verifyNoMoreInteractions(camundaClient, processTestContext, decisionInstanceAssert);
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

    final DecisionInstanceAssert decisionInstanceAssert = assertionFacade.assertThatDecision(any());
    verify(decisionInstanceAssert).hasMatchedRules(1, 3, 5);

    verifyNoMoreInteractions(camundaClient, processTestContext, decisionInstanceAssert);
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

    final DecisionInstanceAssert decisionInstanceAssert = assertionFacade.assertThatDecision(any());
    verify(decisionInstanceAssert).hasNotMatchedRules(2, 4);

    verifyNoMoreInteractions(camundaClient, processTestContext, decisionInstanceAssert);
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

    final DecisionInstanceAssert decisionInstanceAssert = assertionFacade.assertThatDecision(any());
    verify(decisionInstanceAssert).hasNoMatchedRules();

    verifyNoMoreInteractions(camundaClient, processTestContext, decisionInstanceAssert);
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
    verify(assertionFacade).assertThatDecision(any());

    final DecisionInstanceAssert decisionInstanceAssert = assertionFacade.assertThatDecision(any());
    verify(decisionInstanceAssert).hasOutput("valid");
    verify(decisionInstanceAssert).hasMatchedRules(1, 3);
    verify(decisionInstanceAssert).hasNotMatchedRules(2, 4);

    verifyNoMoreInteractions(camundaClient, processTestContext, decisionInstanceAssert);
  }
}
