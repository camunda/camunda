/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.instructions.ImmutableMockDmnDecisionInstruction;
import io.camunda.process.test.api.testCases.instructions.MockDmnDecisionInstruction;
import io.camunda.process.test.impl.testCases.instructions.MockDmnDecisionInstructionHandler;
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
