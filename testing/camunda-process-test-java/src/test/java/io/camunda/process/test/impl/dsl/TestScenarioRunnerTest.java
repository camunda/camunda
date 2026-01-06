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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.ImmutableProcessDefinitionSelector;
import io.camunda.process.test.api.dsl.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.dsl.ImmutableTestCase;
import io.camunda.process.test.api.dsl.TestCase;
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import io.camunda.process.test.api.dsl.TestScenarioRunner;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableCreateProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.assertProcessInstance.ProcessInstanceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TestScenarioRunnerTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_MOCKS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Test
  void shouldExecuteInstruction() {
    // given
    final TestScenarioRunner runner = new CamundaTestScenarioRunner(processTestContext);
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
    final TestScenarioRunner runner = new CamundaTestScenarioRunner(processTestContext);

    final TestCase testCaseWithoutInstructions = ImmutableTestCase.builder().name("test").build();

    // when/then
    assertThatCode(() -> runner.run(testCaseWithoutInstructions)).doesNotThrowAnyException();
  }

  @Test
  void shouldFailIfInstructionIsUnknown() {
    // given
    final TestScenarioRunner runner = new CamundaTestScenarioRunner(processTestContext);

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
    final TestScenarioRunner runner = new CamundaTestScenarioRunner(processTestContext);

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
        .isInstanceOf(TestScenarioRunException.class)
        .hasMessageContaining(
            "Failed to execute instruction '%s': %s", instruction.getType(), instruction.toString())
        .hasCause(clientException);
  }

  @Test
  void shouldThrowAssertionError() {
    // given
    final TestScenarioRunner runner =
        new CamundaTestScenarioRunner(processTestContext, assertionFacade);

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
