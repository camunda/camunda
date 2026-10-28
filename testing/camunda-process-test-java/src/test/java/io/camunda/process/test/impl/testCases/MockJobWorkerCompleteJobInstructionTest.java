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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import io.camunda.process.test.api.mock.VariablesTable;
import io.camunda.process.test.api.testCases.ImmutableVariablesTableRow;
import io.camunda.process.test.api.testCases.instructions.ImmutableMockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.api.testCases.instructions.MockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.impl.testCases.instructions.MockJobWorkerCompleteJobInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockJobWorkerCompleteJobInstructionTest {

  private static final String JOB_TYPE = "send-email";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient evaluationClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock private JobWorkerMockBuilder jobWorkerMockBuilder;

  @Mock private JobWorkerMock jobWorkerMock;

  private final MockJobWorkerCompleteJobInstructionHandler instructionHandler =
      new MockJobWorkerCompleteJobInstructionHandler();

  @BeforeEach
  void setUp() {
    // lenient: the exclusivity-rejection tests throw before mockJobWorker is ever called
    lenient().when(processTestContext.mockJobWorker(JOB_TYPE)).thenReturn(jobWorkerMockBuilder);
    lenient().when(processTestContext.createClient()).thenReturn(evaluationClient);
  }

  @Test
  void shouldCompleteJobWithoutVariables() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder().jobType(JOB_TYPE).build();

    when(jobWorkerMockBuilder.thenComplete(Collections.emptyMap())).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenComplete(Collections.emptyMap());

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldCompleteJobWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("status", "sent");
    variables.put("timestamp", 123456789L);

    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .putAllVariables(variables)
            .build();

    when(jobWorkerMockBuilder.thenComplete(variables)).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenComplete(variables);

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldCompleteJobWithExampleData() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .useExampleData(true)
            .build();

    when(jobWorkerMockBuilder.thenCompleteWithExampleData()).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenCompleteWithExampleData();

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldIgnoreVariablesWhenUseExampleDataIsTrue() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("status", "sent");

    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .putAllVariables(variables)
            .useExampleData(true)
            .build();

    when(jobWorkerMockBuilder.thenCompleteWithExampleData()).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenCompleteWithExampleData();

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldCompleteJobWithVariablesTable() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .addVariablesTable(
                ImmutableVariablesTableRow.builder()
                    .putWhen("loopCounter", 1)
                    .putVariables("sampleType", "regolith")
                    .build())
            .addVariablesTable(
                ImmutableVariablesTableRow.builder().putVariables("sampleType", "basalt").build())
            .build();

    final ArgumentCaptor<VariablesTable> tableCaptor =
        ArgumentCaptor.forClass(VariablesTable.class);
    when(jobWorkerMockBuilder.thenComplete(tableCaptor.capture())).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenComplete(any(VariablesTable.class));

    final VariablesTable table = tableCaptor.getValue();
    assertThat(table.getRows()).hasSize(2);
    assertThat(table.getRows().get(0).getWhen()).containsExactly(entry("loopCounter", 1));
    assertThat(table.getRows().get(0).getVariables())
        .containsExactly(entry("sampleType", "regolith"));
    assertThat(table.getRows().get(1).getWhen()).isEmpty();
    assertThat(table.getRows().get(1).getVariables())
        .containsExactly(entry("sampleType", "basalt"));
  }

  @Test
  void shouldCompleteJobWithVariablesExpression() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .variablesExpression("{ result: 1 }")
            .build();

    final ArgumentCaptor<Function<Map<String, Object>, Map<String, Object>>> functionCaptor =
        ArgumentCaptor.forClass(Function.class);
    when(jobWorkerMockBuilder.thenComplete(functionCaptor.capture())).thenReturn(jobWorkerMock);

    final Map<String, Object> jobVariables = Collections.singletonMap("x", 1);
    final Map<String, Object> feelResult = Collections.singletonMap("result", 1);

    when(evaluationClient
            .newEvaluateExpressionCommand()
            .expression("={ result: 1 }")
            .variables(jobVariables)
            .send()
            .join()
            .getResult())
        .thenReturn(feelResult);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder)
        .thenComplete(ArgumentMatchers.<Function<Map<String, Object>, Map<String, Object>>>any());

    final Function<Map<String, Object>, Map<String, Object>> variablesSupplier =
        functionCaptor.getValue();
    final Map<String, Object> result = variablesSupplier.apply(jobVariables);

    // the stub only returns feelResult for the exact expression/variables combination, so a
    // matching result proves the right expression and variables were sent
    assertThat(result).isEqualTo(feelResult);
  }

  @Test
  void shouldEvaluateExpressionWithContextManagedClientInsteadOfRunnerClient() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .variablesExpression("{ result: 1 }")
            .build();

    final ArgumentCaptor<Function<Map<String, Object>, Map<String, Object>>> functionCaptor =
        ArgumentCaptor.forClass(Function.class);
    when(jobWorkerMockBuilder.thenComplete(functionCaptor.capture())).thenReturn(jobWorkerMock);

    final Map<String, Object> jobVariables = Collections.singletonMap("x", 1);
    final Map<String, Object> feelResult = Collections.singletonMap("result", 1);
    when(evaluationClient
            .newEvaluateExpressionCommand()
            .expression("={ result: 1 }")
            .variables(jobVariables)
            .send()
            .join()
            .getResult())
        .thenReturn(feelResult);

    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // when the runner has finished and closed its client before the job is activated
    camundaClient.close();
    final Map<String, Object> result = functionCaptor.getValue().apply(jobVariables);

    // then
    assertThat(result).isEqualTo(feelResult);
    verify(camundaClient, never()).newEvaluateExpressionCommand();
  }

  @Test
  void shouldNotDoublePrefixExpressionThatAlreadyStartsWithEquals() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .variablesExpression("={ result: 1 }")
            .build();

    final ArgumentCaptor<Function<Map<String, Object>, Map<String, Object>>> functionCaptor =
        ArgumentCaptor.forClass(Function.class);
    when(jobWorkerMockBuilder.thenComplete(functionCaptor.capture())).thenReturn(jobWorkerMock);

    final Map<String, Object> jobVariables = Collections.singletonMap("x", 1);
    when(evaluationClient
            .newEvaluateExpressionCommand()
            .expression("={ result: 1 }")
            .variables(jobVariables)
            .send()
            .join()
            .getResult())
        .thenReturn(Collections.singletonMap("result", 1));

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);
    final Map<String, Object> result = functionCaptor.getValue().apply(jobVariables);

    // then: the expression was evaluated as-is, not prefixed with a second '=' (the stub only
    // matches the single-'=' expression)
    assertThat(result).isEqualTo(Collections.singletonMap("result", 1));
  }

  @Test
  void shouldFailWhenExpressionResultIsNotAMap() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .variablesExpression("1 + 1")
            .build();

    final ArgumentCaptor<Function<Map<String, Object>, Map<String, Object>>> functionCaptor =
        ArgumentCaptor.forClass(Function.class);
    when(jobWorkerMockBuilder.thenComplete(functionCaptor.capture())).thenReturn(jobWorkerMock);

    final Map<String, Object> jobVariables = Collections.emptyMap();
    when(evaluationClient
            .newEvaluateExpressionCommand()
            .expression("=1 + 1")
            .variables(jobVariables)
            .send()
            .join()
            .getResult())
        .thenReturn(2);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    assertThatThrownBy(() -> functionCaptor.getValue().apply(jobVariables))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldFailWhenExpressionResultIsNull() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .variablesExpression("missingVariable")
            .build();

    final ArgumentCaptor<Function<Map<String, Object>, Map<String, Object>>> functionCaptor =
        ArgumentCaptor.forClass(Function.class);
    when(jobWorkerMockBuilder.thenComplete(functionCaptor.capture())).thenReturn(jobWorkerMock);

    final Map<String, Object> jobVariables = Collections.emptyMap();
    when(evaluationClient
            .newEvaluateExpressionCommand()
            .expression("=missingVariable")
            .variables(jobVariables)
            .send()
            .join()
            .getResult())
        .thenReturn(null);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    assertThatThrownBy(() -> functionCaptor.getValue().apply(jobVariables))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectVariablesExpressionWithVariablesTable() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .variablesExpression("{}")
            .addVariablesTable(ImmutableVariablesTableRow.builder().putVariables("a", 1).build())
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectVariablesExpressionWithVariables() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .variablesExpression("{}")
            .putVariables("a", 1)
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectVariablesExpressionWithUseExampleData() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .variablesExpression("{}")
            .useExampleData(true)
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectVariablesTableWithVariables() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .addVariablesTable(ImmutableVariablesTableRow.builder().putVariables("a", 1).build())
            .putVariables("b", 2)
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectVariablesTableWithUseExampleData() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .addVariablesTable(ImmutableVariablesTableRow.builder().putVariables("a", 1).build())
            .useExampleData(true)
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldCompleteWithVariablesWhenVariablesTableIsEmptyAndVariablesIsSet() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("status", "sent");

    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .putAllVariables(variables)
            .build();

    when(jobWorkerMockBuilder.thenComplete(variables)).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenComplete(variables);
  }
}
