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
package io.camunda.process.test.impl.testresult;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.utils.FlowNodeInstanceBuilder;
import io.camunda.process.test.utils.IncidentBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

public class CamundaProcessResultPrinterTest {

  @Test
  void shouldPrintEmptyResult() {
    // given
    final ProcessTestResult emptyTestResult = new ProcessTestResult();

    // when
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestResultPrinter resultPrinter =
        new CamundaProcessTestResultPrinter(outputBuilder::append);

    resultPrinter.print(emptyTestResult);

    // then
    assertThat(outputBuilder.toString())
        .isEqualTo(
            "Process test results:\n"
                + "=====================\n"
                + "\n\n"
                + "=====================\n");
  }

  @Test
  void shouldPrintProcessInstances() {
    // given
    final ProcessTestResult processTestResult = new ProcessTestResult();

    final ProcessInstanceResult processInstance1 = new ProcessInstanceResult();
    processInstance1.setProcessInstance(
        ProcessInstanceBuilder.newActiveProcessInstance(1L)
            .setProcessDefinitionId("process-a")
            .build());

    final ProcessInstanceResult processInstance2 = new ProcessInstanceResult();
    processInstance2.setProcessInstance(
        ProcessInstanceBuilder.newCompletedProcessInstance(2L)
            .setProcessDefinitionId("process-b")
            .build());

    processTestResult.setProcessInstanceTestResults(
        Arrays.asList(processInstance1, processInstance2));

    // when
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestResultPrinter resultPrinter =
        new CamundaProcessTestResultPrinter(outputBuilder::append);

    resultPrinter.print(processTestResult);

    // then
    assertThat(outputBuilder.toString())
        .isEqualTo(
            "Process test results:\n"
                + "=====================\n"
                + "\n"
                + "Process instance: 1 [process-id: 'process-a', state: active]\n"
                + "\n"
                + "Active elements:\n"
                + "<None>\n"
                + "\n"
                + "Variables:\n"
                + "<None>\n"
                + "\n"
                + "Open incidents:\n"
                + "<None>\n"
                + "---------------------\n"
                + "\n"
                + "Process instance: 2 [process-id: 'process-b', state: completed]\n"
                + "\n"
                + "Active elements:\n"
                + "<None>\n"
                + "\n"
                + "Variables:\n"
                + "<None>\n"
                + "\n"
                + "Open incidents:\n"
                + "<None>\n"
                + "=====================\n");
  }

  @Test
  void shouldPrintProcessInstanceVariables() {
    // given
    final ProcessTestResult processTestResult = new ProcessTestResult();

    final ProcessInstanceResult processInstance1 = newProcessInstance(1L, "process-a");
    final Map<String, String> variables1 = new HashMap<>();
    variables1.put("var-1", "1");
    processInstance1.setVariables(variables1);

    final ProcessInstanceResult processInstance2 = newProcessInstance(2L, "process-b");
    final Map<String, String> variables2 = new HashMap<>();
    variables2.put("var-2", "2");
    processInstance2.setVariables(variables2);

    processTestResult.setProcessInstanceTestResults(
        Arrays.asList(processInstance1, processInstance2));

    // when
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestResultPrinter resultPrinter =
        new CamundaProcessTestResultPrinter(outputBuilder::append);

    resultPrinter.print(processTestResult);

    // then
    assertThat(outputBuilder.toString())
        .containsSubsequence(
            "Process instance: 1 [process-id: 'process-a', state: active]\n",
            "Variables:\n",
            "- 'var-1': 1\n",
            "Process instance: 2 [process-id: 'process-b', state: active]\n",
            "Variables:\n",
            "- 'var-2': 2\n");
  }

  @Test
  void shouldPrintOpenIncidents() {
    // given
    final ProcessTestResult processTestResult = new ProcessTestResult();

    final ProcessInstanceResult processInstance1 = newProcessInstance(1L, "process-a");
    processInstance1.setOpenIncidents(
        Arrays.asList(
            IncidentBuilder.newActiveIncident(IncidentErrorType.JOB_NO_RETRIES, "No retries left.")
                .setFlowNodeId("task-a")
                .build(),
            IncidentBuilder.newActiveIncident(
                    IncidentErrorType.EXTRACT_VALUE_ERROR, "Failed to evaluate expression.")
                .setFlowNodeId("task-b")
                .build()));

    final ProcessInstanceResult processInstance2 = newProcessInstance(2L, "process-b");
    processInstance2.setOpenIncidents(
        Collections.singletonList(
            IncidentBuilder.newActiveIncident(
                    IncidentErrorType.UNHANDLED_ERROR_EVENT, "No error catch event found.")
                .setFlowNodeId("task-c")
                .build()));

    processTestResult.setProcessInstanceTestResults(
        Arrays.asList(processInstance1, processInstance2));

    // when
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestResultPrinter resultPrinter =
        new CamundaProcessTestResultPrinter(outputBuilder::append);

    resultPrinter.print(processTestResult);

    // then
    assertThat(outputBuilder.toString())
        .containsSubsequence(
            "Process instance: 1 [process-id: 'process-a', state: active]\n",
            "Open incidents:\n",
            "- 'task-a' [type: JOB_NO_RETRIES] \"No retries left.\"\n",
            "- 'task-b' [type: EXTRACT_VALUE_ERROR] \"Failed to evaluate expression.\"\n",
            "Process instance: 2 [process-id: 'process-b', state: active]\n",
            "Open incidents:\n",
            "- 'task-c' [type: UNHANDLED_ERROR_EVENT] \"No error catch event found.\"\n");
  }

  @Test
  void shouldPrintActiveFlowNodeInstances() {
    // given
    final ProcessTestResult processTestResult = new ProcessTestResult();

    final ProcessInstanceResult processInstance1 = newProcessInstance(1L, "process-a");
    processInstance1.setActiveFlowNodeInstances(
        Arrays.asList(
            FlowNodeInstanceBuilder.newActiveFlowNodeInstance("A", 1L).build(),
            FlowNodeInstanceBuilder.newActiveFlowNodeInstance("B", 1L).build()));

    final ProcessInstanceResult processInstance2 = newProcessInstance(2L, "process-b");
    processInstance2.setActiveFlowNodeInstances(
        Arrays.asList(
            FlowNodeInstanceBuilder.newActiveFlowNodeInstance("C", 2L).build(),
            FlowNodeInstanceBuilder.newActiveFlowNodeInstance("D", 2L)
                .setFlowNodeName(null)
                .build()));

    processTestResult.setProcessInstanceTestResults(
        Arrays.asList(processInstance1, processInstance2));

    // when
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestResultPrinter resultPrinter =
        new CamundaProcessTestResultPrinter(outputBuilder::append);

    resultPrinter.print(processTestResult);

    // then
    assertThat(outputBuilder.toString())
        .containsSubsequence(
            "Process instance: 1 [process-id: 'process-a', state: active]\n",
            "Active elements:\n",
            "- 'A' [name: 'element_A']\n",
            "- 'B' [name: 'element_B']\n",
            "Process instance: 2 [process-id: 'process-b', state: active]\n",
            "Active elements:\n",
            "- 'C' [name: 'element_C']\n",
            "- 'D' [name: '']\n");
  }

  @Test
  void shouldAbbreviateBigVariableValue() {
    // given
    final ProcessTestResult processTestResult = new ProcessTestResult();
    final ProcessInstanceResult processInstance = newProcessInstance(1L, "process-a");
    final Map<String, String> variables = new HashMap<>();

    final String bigVariableValue = StringUtils.repeat("x", 1000);
    variables.put("var", bigVariableValue);

    processInstance.setVariables(variables);
    processTestResult.setProcessInstanceTestResults(Collections.singletonList(processInstance));

    // when
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestResultPrinter resultPrinter =
        new CamundaProcessTestResultPrinter(outputBuilder::append);

    resultPrinter.print(processTestResult);

    // then
    final String expectedVariableValue = StringUtils.abbreviate(bigVariableValue, 500);
    assertThat(outputBuilder.toString())
        .containsSequence("Variables:\n", "- 'var': " + expectedVariableValue);
  }

  @Test
  void shouldAbbreviateBigIncidentMessage() {
    // given
    final ProcessTestResult processTestResult = new ProcessTestResult();
    final ProcessInstanceResult processInstance = newProcessInstance(1L, "process-a");

    final String bigIncidentMessage = StringUtils.repeat("x", 1000);

    processInstance.setOpenIncidents(
        Collections.singletonList(
            IncidentBuilder.newActiveIncident(IncidentErrorType.JOB_NO_RETRIES, bigIncidentMessage)
                .setFlowNodeId("task-a")));
    processTestResult.setProcessInstanceTestResults(Collections.singletonList(processInstance));

    // when
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestResultPrinter resultPrinter =
        new CamundaProcessTestResultPrinter(outputBuilder::append);

    resultPrinter.print(processTestResult);

    // then
    final String expectedIncidentMessage = StringUtils.abbreviate(bigIncidentMessage, 500);
    assertThat(outputBuilder.toString())
        .containsSequence(
            "Open incidents:\n", "- 'task-a' [type: JOB_NO_RETRIES] \"" + expectedIncidentMessage);
  }

  private static ProcessInstanceResult newProcessInstance(
      final long processInstanceKey, final String processId) {
    final ProcessInstanceResult processInstanceResult = new ProcessInstanceResult();
    final ProcessInstance processInstance =
        ProcessInstanceBuilder.newActiveProcessInstance(processInstanceKey)
            .setProcessDefinitionId(processId)
            .build();
    processInstanceResult.setProcessInstance(processInstance);
    return processInstanceResult;
  }
}
