/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.ImmutableProcessDefinitionSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.ImmutableTestCase;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseInstruction;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableCreateProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.assertProcessInstance.ProcessInstanceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TestCaseRunnerTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_MOCKS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Test
  void shouldExecuteInstruction() {
    // given
    final TestCaseRunner runner = new CamundaTestCaseRunner(processTestContext);
    when(processTestContext.createClient()).thenReturn(camundaClient);

    final TestCase testCase =
        createTestCase(
            ImmutableCreateProcessInstanceInstruction.builder()
                .processDefinitionSelector(
                    ImmutableProcessDefinitionSelector.builder()
                        .processDefinitionId("process")
                        .build())
                .build());

    // when
    runner.run(testCase);

    // then
    verify(camundaClient).newCreateInstanceCommand();
  }

  @Test
  void shouldIgnoreEmptyInstructions() {
    // given
    final TestCaseRunner runner = new CamundaTestCaseRunner(processTestContext);

    final TestCase testCaseWithoutInstructions = ImmutableTestCase.builder().name("test").build();

    // when/then
    assertThatCode(() -> runner.run(testCaseWithoutInstructions)).doesNotThrowAnyException();
  }

  @Test
  void shouldFailIfInstructionIsUnknown() {
    // given
    final TestCaseRunner runner = new CamundaTestCaseRunner(processTestContext);

    final TestCaseInstruction unknownInstruction = mock(TestCaseInstruction.class);
    final TestCase testCase = createTestCase(unknownInstruction);

    // when/then
    assertThatThrownBy(() -> runner.run(testCase))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining(
            "No handler found for instruction: %s", unknownInstruction.getClass());
  }

  @Test
  void shouldFailIfInstructionExecutionThrowsException() {
    // given
    final TestCaseRunner runner = new CamundaTestCaseRunner(processTestContext);

    when(processTestContext.createClient()).thenReturn(camundaClient);
    final ClientException clientException = new ClientException("expected");
    when(camundaClient.newCreateInstanceCommand()).thenThrow(clientException);

    final TestCaseInstruction instruction =
        ImmutableCreateProcessInstanceInstruction.builder()
            .processDefinitionSelector(
                ImmutableProcessDefinitionSelector.builder().processDefinitionId("process").build())
            .build();
    final TestCase testCase = createTestCase(instruction);

    // when/then
    assertThatThrownBy(() -> runner.run(testCase))
        .isInstanceOf(TestCaseRunException.class)
        .hasMessageContaining(
            "Failed to execute instruction '%s': %s", instruction.getType(), instruction.toString())
        .hasCause(clientException);
  }

  @Test
  void shouldThrowAssertionError() {
    // given
    final TestCaseRunner runner = new CamundaTestCaseRunner(processTestContext, assertionFacade);

    final AssertionError assertionError = new AssertionError("expected");
    when(assertionFacade.assertThatProcessInstance(any())).thenThrow(assertionError);

    final TestCaseInstruction instruction =
        ImmutableAssertProcessInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder().processDefinitionId("process").build())
            .state(ProcessInstanceState.IS_CREATED)
            .build();
    final TestCase testCase = createTestCase(instruction);

    // when/then
    assertThatThrownBy(() -> runner.run(testCase))
        .isInstanceOf(AssertionError.class)
        .isEqualTo(assertionError);
  }

  private static TestCase createTestCase(final TestCaseInstruction instruction) {
    return ImmutableTestCase.builder().name("test").addInstructions(instruction).build();
  }
}
