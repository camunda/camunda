/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testresult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import io.camunda.process.test.impl.client.VariableDto;
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
        .allMatch(processInstanceResult -> processInstanceResult.getVariables().isEmpty());
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
}
