/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.ImmutableElementSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.ImmutableUpdateVariablesInstruction;
import io.camunda.process.test.api.testCases.instructions.UpdateVariablesInstruction;
import io.camunda.process.test.impl.testCases.instructions.UpdateVariablesInstructionHandler;
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
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).updateLocalVariables(any(), any(), eq(variables));
  }
}
