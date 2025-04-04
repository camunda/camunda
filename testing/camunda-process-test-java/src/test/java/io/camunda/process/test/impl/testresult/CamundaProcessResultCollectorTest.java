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
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.FlowNodeInstanceDto;
import io.camunda.process.test.impl.client.FlowNodeInstanceState;
import io.camunda.process.test.impl.client.IncidentDto;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import io.camunda.process.test.impl.client.VariableDto;
import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CamundaProcessResultCollectorTest {

  @Mock private CamundaDataSource camundaDataSource;

  private CamundaProcessTestResultCollector resultCollector;

  @BeforeEach
  void configureMocks() {
    resultCollector = new CamundaProcessTestResultCollector(camundaDataSource);
  }

  @Test
  void shouldReturnEmptyResult() throws IOException {
    // given
    when(camundaDataSource.findProcessInstances()).thenReturn(Collections.emptyList());

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceTestResults()).isEmpty();
  }

  @Test
  void shouldReturnEmptyResultIfDataSourceThrowsException() throws IOException {
    // given
    doThrow(new IOException("expected failure")).when(camundaDataSource).findProcessInstances();

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceTestResults()).isEmpty();
  }

  @Test
  void shouldReturnProcessInstances() throws IOException {
    // given
    final ProcessInstanceDto processInstance1 = newProcessInstance(1L, "process-a");
    final ProcessInstanceDto processInstance2 = newProcessInstance(2L, "process-b");
    when(camundaDataSource.findProcessInstances())
        .thenReturn(Arrays.asList(processInstance1, processInstance2));

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result.getProcessInstanceTestResults())
        .hasSize(2)
        .extracting(
            ProcessInstanceResult::getProcessInstanceKey, ProcessInstanceResult::getProcessId)
        .contains(tuple(1L, "process-a"), tuple(2L, "process-b"));

    assertThat(result.getProcessInstanceTestResults())
        .allMatch(processInstanceResult -> processInstanceResult.getVariables().isEmpty())
        .allMatch(processInstanceResult -> processInstanceResult.getOpenIncidents().isEmpty());
  }

  @Test
  void shouldReturnProcessInstanceVariables() throws IOException {
    // given
    final ProcessInstanceDto processInstance1 = newProcessInstance(1L, "process-a");
    final ProcessInstanceDto processInstance2 = newProcessInstance(2L, "process-b");
    when(camundaDataSource.findProcessInstances())
        .thenReturn(Arrays.asList(processInstance1, processInstance2));

    final VariableDto variable1 = newVariable("var-1", "1");
    final VariableDto variable2 = newVariable("var-2", "2");
    when(camundaDataSource.getVariablesByProcessInstanceKey(processInstance1.getKey()))
        .thenReturn(Arrays.asList(variable1, variable2));

    final VariableDto variable3 = newVariable("var-3", "3");
    when(camundaDataSource.getVariablesByProcessInstanceKey(processInstance2.getKey()))
        .thenReturn(Collections.singletonList(variable3));

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result.getProcessInstanceTestResults()).hasSize(2);

    assertThat(result.getProcessInstanceTestResults().get(0).getVariables())
        .hasSize(2)
        .containsEntry("var-1", "1")
        .containsEntry("var-2", "2");

    assertThat(result.getProcessInstanceTestResults().get(1).getVariables())
        .hasSize(1)
        .containsEntry("var-3", "3");
  }

  @Test
  void shouldReturnProcessInstanceVariablesWithNullValues() throws IOException {
    // given
    final ProcessInstanceDto processInstance1 = newProcessInstance(1L, "process-a");
    when(camundaDataSource.findProcessInstances())
        .thenReturn(Collections.singletonList(processInstance1));

    when(camundaDataSource.getVariablesByProcessInstanceKey(processInstance1.getKey()))
        .thenReturn(
            Arrays.asList(
                newVariable("var-1", "1"), newVariable("var-2", null), newVariable("var-3", null)));

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result.getProcessInstanceTestResults().get(0).getVariables())
        .hasSize(3)
        .containsEntry("var-1", "1")
        .containsEntry("var-2", null)
        .containsEntry("var-3", null);
  }

  @Test
  void shouldReturnOpenIncidents() throws IOException {
    // given
    final ProcessInstanceDto processInstance1 = newProcessInstance(1L, "process-a");
    final ProcessInstanceDto processInstance2 = newProcessInstance(2L, "process-b");
    when(camundaDataSource.findProcessInstances())
        .thenReturn(Arrays.asList(processInstance1, processInstance2));

    final FlowNodeInstanceDto flowNodeInstance1 = newFlowNodeInstance("task-a", "A");
    flowNodeInstance1.setIncident(true);
    flowNodeInstance1.setIncidentKey(10L);

    final FlowNodeInstanceDto flowNodeInstance2 = newFlowNodeInstance("task-b", "B");
    flowNodeInstance2.setIncident(true);
    flowNodeInstance2.setIncidentKey(11L);

    when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(processInstance1.getKey()))
        .thenReturn(Arrays.asList(flowNodeInstance1, flowNodeInstance2));

    final FlowNodeInstanceDto flowNodeInstance3 = newFlowNodeInstance("task-c", "C");
    flowNodeInstance3.setIncident(true);
    flowNodeInstance3.setIncidentKey(12L);

    final FlowNodeInstanceDto flowNodeInstance4 = newFlowNodeInstance("task-d", "D");
    flowNodeInstance4.setIncident(false);

    when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(processInstance2.getKey()))
        .thenReturn(Arrays.asList(flowNodeInstance3, flowNodeInstance4));

    final IncidentDto incident1 = newIncident("JOB_NO_RETRIES", "No retries left.");
    final IncidentDto incident2 =
        newIncident("EXTRACT_VALUE_ERROR", "Failed to evaluate expression.");
    final IncidentDto incident3 =
        newIncident("UNHANDLED_ERROR_EVENT", "No error catch event found.");

    when(camundaDataSource.getIncidentByKey(flowNodeInstance1.getIncidentKey()))
        .thenReturn(incident1);
    when(camundaDataSource.getIncidentByKey(flowNodeInstance2.getIncidentKey()))
        .thenReturn(incident2);
    when(camundaDataSource.getIncidentByKey(flowNodeInstance3.getIncidentKey()))
        .thenReturn(incident3);

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result.getProcessInstanceTestResults()).hasSize(2);

    assertThat(result.getProcessInstanceTestResults().get(0).getOpenIncidents())
        .hasSize(2)
        .extracting(OpenIncident::getType, OpenIncident::getMessage, OpenIncident::getFlowNodeId)
        .contains(
            tuple("JOB_NO_RETRIES", "No retries left.", "task-a"),
            tuple("EXTRACT_VALUE_ERROR", "Failed to evaluate expression.", "task-b"));

    assertThat(result.getProcessInstanceTestResults().get(1).getOpenIncidents())
        .hasSize(1)
        .extracting(OpenIncident::getType, OpenIncident::getMessage, OpenIncident::getFlowNodeId)
        .contains(tuple("UNHANDLED_ERROR_EVENT", "No error catch event found.", "task-c"));
  }

  @Test
  void shouldReturnActiveFlowNodeInstances() throws IOException {
    // given
    final ProcessInstanceDto processInstance1 = newProcessInstance(1L, "process-a");
    final ProcessInstanceDto processInstance2 = newProcessInstance(2L, "process-b");
    when(camundaDataSource.findProcessInstances())
        .thenReturn(Arrays.asList(processInstance1, processInstance2));

    final FlowNodeInstanceDto flowNodeInstance1 = newFlowNodeInstance("task-a", "A");
    final FlowNodeInstanceDto flowNodeInstance2 = newFlowNodeInstance("task-b", "B");

    when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(processInstance1.getKey()))
        .thenReturn(Arrays.asList(flowNodeInstance1, flowNodeInstance2));

    final FlowNodeInstanceDto flowNodeInstance3 = newFlowNodeInstance("task-c", "C");
    final FlowNodeInstanceDto flowNodeInstance4 = newFlowNodeInstance("task-d", "D");
    flowNodeInstance4.setState(FlowNodeInstanceState.COMPLETED);

    when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(processInstance2.getKey()))
        .thenReturn(Arrays.asList(flowNodeInstance3, flowNodeInstance4));

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result.getProcessInstanceTestResults()).hasSize(2);

    assertThat(result.getProcessInstanceTestResults().get(0).getActiveFlowNodeInstances())
        .hasSize(2)
        .extracting(FlowNodeInstance::getFlowNodeId, FlowNodeInstance::getFlowNodeName)
        .contains(tuple("task-a", "A"), tuple("task-b", "B"));

    assertThat(result.getProcessInstanceTestResults().get(1).getActiveFlowNodeInstances())
        .hasSize(1)
        .extracting(FlowNodeInstance::getFlowNodeId, FlowNodeInstance::getFlowNodeName)
        .contains(tuple("task-c", "C"));
  }

  private static ProcessInstanceDto newProcessInstance(
      final long processInstanceKey, final String processId) {
    final ProcessInstanceDto processInstance = new ProcessInstanceDto();
    processInstance.setKey(processInstanceKey);
    processInstance.setBpmnProcessId(processId);
    return processInstance;
  }

  private static VariableDto newVariable(final String variableName, final String variableValue) {
    final VariableDto variable = new VariableDto();
    variable.setName(variableName);
    variable.setValue(variableValue);
    return variable;
  }

  private static FlowNodeInstanceDto newFlowNodeInstance(
      final String nodeId, final String nodeName) {
    final FlowNodeInstanceDto flowNodeInstance = new FlowNodeInstanceDto();
    flowNodeInstance.setFlowNodeId(nodeId);
    flowNodeInstance.setFlowNodeName(nodeName);
    flowNodeInstance.setState(FlowNodeInstanceState.ACTIVE);
    return flowNodeInstance;
  }

  private static IncidentDto newIncident(final String type, final String message) {
    final IncidentDto incident = new IncidentDto();
    incident.setType(type);
    incident.setMessage(message);
    return incident;
  }
}
