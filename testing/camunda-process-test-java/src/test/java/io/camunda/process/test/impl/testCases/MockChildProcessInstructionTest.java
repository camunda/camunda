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
import io.camunda.process.test.api.testCases.instructions.ImmutableMockChildProcessInstruction;
import io.camunda.process.test.api.testCases.instructions.MockChildProcessInstruction;
import io.camunda.process.test.impl.testCases.instructions.MockChildProcessInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockChildProcessInstructionTest {

  private static final String CHILD_PROCESS_ID = "child-process";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final MockChildProcessInstructionHandler instructionHandler =
      new MockChildProcessInstructionHandler();

  @Test
  void shouldMockChildProcessWithoutVariables() {
    // given
    final MockChildProcessInstruction instruction =
        ImmutableMockChildProcessInstruction.builder()
            .processDefinitionId(CHILD_PROCESS_ID)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockChildProcess(CHILD_PROCESS_ID, Collections.emptyMap());

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldMockChildProcessWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("amount", 100.0);
    variables.put("currency", "USD");

    final MockChildProcessInstruction instruction =
        ImmutableMockChildProcessInstruction.builder()
            .processDefinitionId(CHILD_PROCESS_ID)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockChildProcess(CHILD_PROCESS_ID, variables);

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }
}
