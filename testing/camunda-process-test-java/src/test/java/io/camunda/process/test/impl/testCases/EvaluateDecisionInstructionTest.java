/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.EvaluateDecisionCommandStep1.EvaluateDecisionCommandStep2;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.ImmutableDecisionDefinitionSelector;
import io.camunda.process.test.api.testCases.instructions.EvaluateDecisionInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableEvaluateDecisionInstruction;
import io.camunda.process.test.impl.testCases.instructions.EvaluateDecisionInstructionHandler;
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
