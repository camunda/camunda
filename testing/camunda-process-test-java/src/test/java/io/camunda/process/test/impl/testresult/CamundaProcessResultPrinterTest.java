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

import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.FlowNodeInstanceState;
import io.camunda.process.test.impl.client.FlowNodeInstanceDto;
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
    final ProcessInstanceResult processInstance1 = newProcessInstance(1L, "process-a");
    final ProcessInstanceResult processInstance2 = newProcessInstance(2L, "process-b");

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
                + "Process instance: 1 [process-id: 'process-a']\n"
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
                + "Process instance: 2 [process-id: 'process-b']\n"
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
            "Process instance: 1 [process-id: 'process-a']\n",
            "Variables:\n",
            "- 'var-1': 1\n",
            "Process instance: 2 [process-id: 'process-b']\n",
            "Variables:\n",
            "- 'var-2': 2\n");
  }

  @Test
  void shouldPrintOpenIncidents() {
    // given
    final ProcessTestResult processTestResult = new ProcessTestResult();

    final ProcessInstanceResult processInstance1 = newProcessInstance(1L, "process-a");
    final OpenIncident incident1 = newOpenIncident("JOB_NO_RETRIES", "No retries left.", "task-a");
    final OpenIncident incident2 =
        newOpenIncident("EXTRACT_VALUE_ERROR", "Failed to evaluate expression.", "task-b");
    processInstance1.setOpenIncidents(Arrays.asList(incident1, incident2));

    final ProcessInstanceResult processInstance2 = newProcessInstance(2L, "process-b");
    final OpenIncident incident3 =
        newOpenIncident("UNHANDLED_ERROR_EVENT", "No error catch event found.", "task-c");
    processInstance2.setOpenIncidents(Collections.singletonList(incident3));

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
            "Process instance: 1 [process-id: 'process-a']\n",
            "Open incidents:\n",
            "- 'task-a' [type: JOB_NO_RETRIES] \"No retries left.\"\n",
            "- 'task-b' [type: EXTRACT_VALUE_ERROR] \"Failed to evaluate expression.\"\n",
            "Process instance: 2 [process-id: 'process-b']\n",
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
            newActiveFlowNodeInstance("task_A", "A"), newActiveFlowNodeInstance("task_B", "B")));

    final ProcessInstanceResult processInstance2 = newProcessInstance(2L, "process-b");
    processInstance2.setActiveFlowNodeInstances(
        Arrays.asList(
            newActiveFlowNodeInstance("task_C", "C"), newActiveFlowNodeInstance("task_D", null)));

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
            "Process instance: 1 [process-id: 'process-a']\n",
            "Active elements:\n",
            "- 'task_A' [name: 'A']\n",
            "- 'task_B' [name: 'B']\n",
            "Process instance: 2 [process-id: 'process-b']\n",
            "Active elements:\n",
            "- 'task_C' [name: 'C']\n",
            "- 'task_D' [name: '']\n");
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
    final OpenIncident incident = newOpenIncident("JOB_NO_RETRIES", bigIncidentMessage, "task-a");

    processInstance.setOpenIncidents(Collections.singletonList(incident));
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
    final ProcessInstanceResult processInstance = new ProcessInstanceResult();
    processInstance.setProcessInstanceKey(processInstanceKey);
    processInstance.setProcessId(processId);
    return processInstance;
  }

  private static OpenIncident newOpenIncident(
      final String type, final String message, final String flowNodeId) {
    final OpenIncident incident = new OpenIncident();
    incident.setType(type);
    incident.setMessage(message);
    incident.setFlowNodeId(flowNodeId);
    return incident;
  }

  private static FlowNodeInstance newActiveFlowNodeInstance(final String id, final String name) {
    final FlowNodeInstanceDto flowNodeInstance = new FlowNodeInstanceDto();
    flowNodeInstance.setFlowNodeId(id);
    flowNodeInstance.setFlowNodeName(name);
    flowNodeInstance.setState(FlowNodeInstanceState.ACTIVE);
    return flowNodeInstance;
  }
}
