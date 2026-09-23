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
package io.camunda.process.test.impl.testCases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.ImmutableProcessDefinitionSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.ImmutableTestCase;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseInstruction;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableCreateProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableIncreaseTimeInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableMockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.api.testCases.instructions.assertProcessInstance.ProcessInstanceState;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TestCaseRunnerTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
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
  void shouldCancelIsolatedProcessInstance() {
    // given
    final TestCaseRunner runner = new CamundaTestCaseRunner(processTestContext);
    when(processTestContext.createClient()).thenReturn(camundaClient);
    when(processTestContext.getJobReservationToken()).thenReturn("cpt-1234");

    final ProcessInstanceEvent processInstance = mock(ProcessInstanceEvent.class);
    when(processInstance.getProcessInstanceKey()).thenReturn(42L);
    when(camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(any())
            .latestVersion()
            .variables(anyMap())
            .send()
            .join())
        .thenReturn(processInstance);

    final TestCase testCase =
        createTestCase(
            ImmutableCreateProcessInstanceInstruction.builder()
                .processDefinitionSelector(
                    ImmutableProcessDefinitionSelector.builder()
                        .processDefinitionId("process")
                        .build())
                .reserveJobs(true)
                .build());

    // when
    runner.run(testCase);

    // then
    verify(camundaClient).newCancelInstanceCommand(42L);
  }

  @Test
  void shouldCancelIsolatedProcessInstanceWhenTestCaseFails() {
    // given
    final TestCaseRunner runner = new CamundaTestCaseRunner(processTestContext);
    when(processTestContext.createClient()).thenReturn(camundaClient);
    when(processTestContext.getJobReservationToken()).thenReturn("cpt-1234");

    final ProcessInstanceEvent processInstance = mock(ProcessInstanceEvent.class);
    when(processInstance.getProcessInstanceKey()).thenReturn(42L);
    when(camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(any())
            .latestVersion()
            .variables(anyMap())
            .send()
            .join())
        .thenReturn(processInstance);

    final TestCase testCase =
        ImmutableTestCase.builder()
            .name("test")
            .addInstructions(
                ImmutableCreateProcessInstanceInstruction.builder()
                    .processDefinitionSelector(
                        ImmutableProcessDefinitionSelector.builder()
                            .processDefinitionId("process")
                            .build())
                    .reserveJobs(true)
                    .build())
            .addInstructions(mock(TestCaseInstruction.class))
            .build();

    // when
    assertThatThrownBy(() -> runner.run(testCase)).isInstanceOf(TestCaseRunException.class);

    // then
    verify(camundaClient).newCancelInstanceCommand(42L);
  }

  @Test
  void shouldNotCancelProcessInstanceThatIsNotIsolated() {
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
    verify(camundaClient, never()).newCancelInstanceCommand(anyLong());
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
        .isInstanceOf(TestCaseRunException.class)
        .cause()
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

  @Test
  void shouldDetectThatReservedJobsAreMocked() {
    // given
    final TestCase testCase =
        ImmutableTestCase.builder()
            .name("test")
            .addInstructions(createProcessInstance(true))
            .addInstructions(
                ImmutableMockJobWorkerCompleteJobInstruction.builder()
                    .jobType("charge-card")
                    .build())
            .build();

    // when/then
    assertThat(CamundaTestCaseRunner.reservesJobsAndMocksJobWorkers(testCase)).isTrue();
  }

  @Test
  void shouldNotDetectMockedJobWorkerWithoutReservation() {
    // given
    final TestCase testCase =
        ImmutableTestCase.builder()
            .name("test")
            .addInstructions(createProcessInstance(false))
            .addInstructions(
                ImmutableMockJobWorkerCompleteJobInstruction.builder()
                    .jobType("charge-card")
                    .build())
            .build();

    // when/then
    assertThat(CamundaTestCaseRunner.reservesJobsAndMocksJobWorkers(testCase)).isFalse();
  }

  @Test
  void shouldNotDetectReservationWithoutMockedJobWorker() {
    // given
    final TestCase testCase = createTestCase(createProcessInstance(true));

    // when/then
    assertThat(CamundaTestCaseRunner.reservesJobsAndMocksJobWorkers(testCase)).isFalse();
  }

  @Test
  void shouldDetectThatHeldTimersAreDrivenByTheClock() {
    // given
    final TestCase testCase =
        ImmutableTestCase.builder()
            .name("test")
            .addInstructions(createProcessInstanceHoldingTimers(true))
            .addInstructions(
                ImmutableIncreaseTimeInstruction.builder().duration(Duration.ofHours(1)).build())
            .build();

    // when/then
    assertThat(CamundaTestCaseRunner.holdsTimersAndMovesTheClock(testCase)).isTrue();
  }

  @Test
  void shouldNotDetectClockMoveWithoutHeldTimers() {
    // given
    final TestCase testCase =
        ImmutableTestCase.builder()
            .name("test")
            .addInstructions(createProcessInstanceHoldingTimers(false))
            .addInstructions(
                ImmutableIncreaseTimeInstruction.builder().duration(Duration.ofHours(1)).build())
            .build();

    // when/then
    assertThat(CamundaTestCaseRunner.holdsTimersAndMovesTheClock(testCase)).isFalse();
  }

  @Test
  void shouldNotDetectHeldTimersWithoutClockMove() {
    // given
    final TestCase testCase = createTestCase(createProcessInstanceHoldingTimers(true));

    // when/then
    assertThat(CamundaTestCaseRunner.holdsTimersAndMovesTheClock(testCase)).isFalse();
  }

  @Test
  void shouldCancelProcessInstanceThatHoldsTimers() {
    // given
    final TestCaseRunner runner = new CamundaTestCaseRunner(processTestContext);
    when(processTestContext.createClient()).thenReturn(camundaClient);

    final ProcessInstanceEvent processInstance = mock(ProcessInstanceEvent.class);
    when(processInstance.getProcessInstanceKey()).thenReturn(42L);
    when(camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(any())
            .latestVersion()
            .variables(anyMap())
            .send()
            .join())
        .thenReturn(processInstance);

    final TestCase testCase = createTestCase(createProcessInstanceHoldingTimers(true));

    // when
    runner.run(testCase);

    // then a held-timer instance parks on a timer that never fires, so it must be cancelled
    verify(camundaClient).newCancelInstanceCommand(42L);
  }

  private static TestCaseInstruction createProcessInstance(final boolean reserveJobs) {
    return ImmutableCreateProcessInstanceInstruction.builder()
        .processDefinitionSelector(
            ImmutableProcessDefinitionSelector.builder().processDefinitionId("process").build())
        .reserveJobs(reserveJobs)
        .build();
  }

  private static TestCaseInstruction createProcessInstanceHoldingTimers(final boolean holdTimers) {
    return ImmutableCreateProcessInstanceInstruction.builder()
        .processDefinitionSelector(
            ImmutableProcessDefinitionSelector.builder().processDefinitionId("process").build())
        .holdTimers(holdTimers)
        .build();
  }

  private static TestCase createTestCase(final TestCaseInstruction instruction) {
    return ImmutableTestCase.builder().name("test").addInstructions(instruction).build();
  }
}
