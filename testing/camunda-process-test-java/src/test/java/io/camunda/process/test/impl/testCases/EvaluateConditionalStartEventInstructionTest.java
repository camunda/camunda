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
import io.camunda.process.test.api.testCases.instructions.EvaluateConditionalStartEventInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableEvaluateConditionalStartEventInstruction;
import io.camunda.process.test.impl.testCases.instructions.EvaluateConditionalStartEventInstructionHandler;
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

    // Get the command after variables is set (Step2)
    // We need to verify against the mock returned by variables()
    verify(camundaClient.newEvaluateConditionalCommand()).variables(variables);

    // Get the Step2 mock to verify send() was called
    verify(camundaClient.newEvaluateConditionalCommand().variables(variables)).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }
}
