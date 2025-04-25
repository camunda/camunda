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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.ElementInstanceBuilder;
import io.camunda.process.test.utils.IncidentBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import io.camunda.process.test.utils.VariableBuilder;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CamundaProcessResultCollectorTest {

  private static final ProcessInstance PROCESS_INSTANCE_1 =
      ProcessInstanceBuilder.newActiveProcessInstance(1L)
          .setProcessDefinitionId("process-a")
          .build();

  private static final ProcessInstance PROCESS_INSTANCE_2 =
      ProcessInstanceBuilder.newActiveProcessInstance(2L)
          .setProcessDefinitionId("process-b")
          .build();

  @Mock private CamundaDataSource camundaDataSource;

  private CamundaProcessTestResultCollector resultCollector;

  @BeforeEach
  void configureMocks() {
    resultCollector = new CamundaProcessTestResultCollector(camundaDataSource);
  }

  @Test
  void shouldReturnEmptyResult() {
    // given
    when(camundaDataSource.findProcessInstances()).thenReturn(Collections.emptyList());

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceTestResults()).isEmpty();
  }

  @Test
  void shouldReturnProcessInstances() {
    // given
    when(camundaDataSource.findProcessInstances())
        .thenReturn(Arrays.asList(PROCESS_INSTANCE_1, PROCESS_INSTANCE_2));

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result.getProcessInstanceTestResults())
        .hasSize(2)
        .extracting(ProcessInstanceResult::getProcessInstance)
        .extracting(ProcessInstance::getProcessInstanceKey, ProcessInstance::getProcessDefinitionId)
        .contains(tuple(1L, "process-a"), tuple(2L, "process-b"));

    assertThat(result.getProcessInstanceTestResults())
        .allMatch(processInstanceResult -> processInstanceResult.getVariables().isEmpty())
        .allMatch(processInstanceResult -> processInstanceResult.getOpenIncidents().isEmpty());
  }

  @Test
  void shouldReturnProcessInstanceVariables() {
    // given
    when(camundaDataSource.findProcessInstances())
        .thenReturn(Arrays.asList(PROCESS_INSTANCE_1, PROCESS_INSTANCE_2));

    when(camundaDataSource.findVariablesByProcessInstanceKey(
            PROCESS_INSTANCE_1.getProcessInstanceKey()))
        .thenReturn(
            Arrays.asList(
                VariableBuilder.newVariable("var-1", "1").build(),
                VariableBuilder.newVariable("var-2", "2").build()));

    when(camundaDataSource.findVariablesByProcessInstanceKey(
            PROCESS_INSTANCE_2.getProcessInstanceKey()))
        .thenReturn(Collections.singletonList(VariableBuilder.newVariable("var-3", "3").build()));

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
  void shouldPassIfProcessInstanceVariablesContainsNullValues() {
    // given
    when(camundaDataSource.findProcessInstances())
        .thenReturn(Collections.singletonList(PROCESS_INSTANCE_1));

    when(camundaDataSource.findVariablesByProcessInstanceKey(
            PROCESS_INSTANCE_1.getProcessInstanceKey()))
        .thenReturn(
            Arrays.asList(
                VariableBuilder.newVariable("var-1", "1").build(),
                VariableBuilder.newVariable("var-2", null).build(),
                VariableBuilder.newVariable("var-3", null).build()));

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
  void shouldReturnOpenIncidents() {
    // given
    when(camundaDataSource.findProcessInstances())
        .thenReturn(Arrays.asList(PROCESS_INSTANCE_1, PROCESS_INSTANCE_2));

    final ElementInstance elementInstance1 =
        ElementInstanceBuilder.newActiveElementInstance(
                "A", PROCESS_INSTANCE_1.getProcessInstanceKey())
            .setIncident(true)
            .setIncidentKey(10L)
            .build();

    final ElementInstance elementInstance2 =
        ElementInstanceBuilder.newActiveElementInstance(
                "B", PROCESS_INSTANCE_1.getProcessInstanceKey())
            .setIncident(true)
            .setIncidentKey(11L)
            .build();

    final ElementInstance elementInstance3 =
        ElementInstanceBuilder.newActiveElementInstance(
                "C", PROCESS_INSTANCE_2.getProcessInstanceKey())
            .setIncident(true)
            .setIncidentKey(12L)
            .build();

    final ElementInstance elementInstance4 =
        ElementInstanceBuilder.newActiveElementInstance(
                "D", PROCESS_INSTANCE_2.getProcessInstanceKey())
            .setIncident(false)
            .build();

    when(camundaDataSource.findElementInstances(any()))
        .thenReturn(Arrays.asList(elementInstance1, elementInstance2))
        .thenReturn(Arrays.asList(elementInstance3, elementInstance4));

    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Arrays.asList(
                IncidentBuilder.newActiveIncident(
                        IncidentErrorType.JOB_NO_RETRIES, "No retries left.")
                    .setElementId("A")
                    .build(),
                IncidentBuilder.newActiveIncident(
                        IncidentErrorType.EXTRACT_VALUE_ERROR, "Failed to evaluate expression.")
                    .setElementId("B")
                    .build()))
        .thenReturn(
            Collections.singletonList(
                IncidentBuilder.newActiveIncident(
                        IncidentErrorType.UNHANDLED_ERROR_EVENT, "No error catch event found.")
                    .setElementId("C")
                    .build()));

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result.getProcessInstanceTestResults()).hasSize(2);

    assertThat(result.getProcessInstanceTestResults().get(0).getOpenIncidents())
        .hasSize(2)
        .extracting(Incident::getErrorType, Incident::getErrorMessage, Incident::getElementId)
        .contains(
            tuple(IncidentErrorType.JOB_NO_RETRIES, "No retries left.", "A"),
            tuple(IncidentErrorType.EXTRACT_VALUE_ERROR, "Failed to evaluate expression.", "B"));

    assertThat(result.getProcessInstanceTestResults().get(1).getOpenIncidents())
        .hasSize(1)
        .extracting(Incident::getErrorType, Incident::getErrorMessage, Incident::getElementId)
        .contains(
            tuple(IncidentErrorType.UNHANDLED_ERROR_EVENT, "No error catch event found.", "C"));
  }

  @Test
  void shouldReturnActiveElementInstances() {
    // given
    when(camundaDataSource.findProcessInstances())
        .thenReturn(Arrays.asList(PROCESS_INSTANCE_1, PROCESS_INSTANCE_2));

    when(camundaDataSource.findElementInstances(any()))
        .thenReturn(
            Arrays.asList(
                ElementInstanceBuilder.newActiveElementInstance(
                        "A", PROCESS_INSTANCE_1.getProcessInstanceKey())
                    .build(),
                ElementInstanceBuilder.newActiveElementInstance(
                        "B", PROCESS_INSTANCE_1.getProcessInstanceKey())
                    .build()))
        .thenReturn(
            Arrays.asList(
                ElementInstanceBuilder.newActiveElementInstance(
                        "C", PROCESS_INSTANCE_2.getProcessInstanceKey())
                    .build(),
                ElementInstanceBuilder.newActiveElementInstance(
                        "D", PROCESS_INSTANCE_2.getProcessInstanceKey())
                    .build()));

    // when
    final ProcessTestResult result = resultCollector.collect();

    // then
    assertThat(result.getProcessInstanceTestResults()).hasSize(2);

    assertThat(result.getProcessInstanceTestResults().get(0).getActiveElementInstances())
        .hasSize(2)
        .extracting(ElementInstance::getElementId, ElementInstance::getElementName)
        .contains(tuple("A", "element_A"), tuple("B", "element_B"));

    assertThat(result.getProcessInstanceTestResults().get(1).getActiveElementInstances())
        .hasSize(2)
        .extracting(ElementInstance::getElementId, ElementInstance::getElementName)
        .contains(tuple("C", "element_C"), tuple("D", "element_D"));
  }
}
