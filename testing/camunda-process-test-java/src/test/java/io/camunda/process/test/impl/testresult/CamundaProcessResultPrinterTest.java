/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testresult;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.extension.CamundaProcessTestResultPrinter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CamundaProcessResultPrinterTest {

  @Test
  void shouldPrintEmptyResult() {
    // given
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestResultPrinter resultPrinter =
        new CamundaProcessTestResultPrinter(outputBuilder::append);

    final ProcessTestResult emptyTestResult = new ProcessTestResult();

    // when
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
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestResultPrinter resultPrinter =
        new CamundaProcessTestResultPrinter(outputBuilder::append);

    final ProcessTestResult processTestResult = new ProcessTestResult();
    final ProcessInstanceResult processInstance1 = newProcessInstance(1L, "process-a");
    final ProcessInstanceResult processInstance2 = newProcessInstance(2L, "process-b");

    processTestResult.setProcessInstanceTestResults(
        Arrays.asList(processInstance1, processInstance2));

    // when
    resultPrinter.print(processTestResult);

    // then
    assertThat(outputBuilder.toString())
        .isEqualTo(
            "Process test results:\n"
                + "=====================\n"
                + "\n"
                + "Process instance: 1 [process-id: 'process-a']\n"
                + "\n"
                + "Variables:\n"
                + "<None>\n"
                + "---------------------\n"
                + "\n"
                + "Process instance: 2 [process-id: 'process-b']\n"
                + "\n"
                + "Variables:\n"
                + "<None>\n"
                + "=====================\n");
  }

  @Test
  void shouldPrintProcessInstanceVariables() {
    // given
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestResultPrinter resultPrinter =
        new CamundaProcessTestResultPrinter(outputBuilder::append);

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
    resultPrinter.print(processTestResult);

    // then
    assertThat(outputBuilder.toString())
        .isEqualTo(
            "Process test results:\n"
                + "=====================\n"
                + "\n"
                + "Process instance: 1 [process-id: 'process-a']\n"
                + "\n"
                + "Variables:\n"
                + "- 'var-1': 1\n"
                + "---------------------\n"
                + "\n"
                + "Process instance: 2 [process-id: 'process-b']\n"
                + "\n"
                + "Variables:\n"
                + "- 'var-2': 2\n"
                + "=====================\n");
  }

  private static ProcessInstanceResult newProcessInstance(
      final long processInstanceKey, final String processId) {
    final ProcessInstanceResult processInstance = new ProcessInstanceResult();
    processInstance.setProcessInstanceKey(processInstanceKey);
    processInstance.setProcessId(processId);
    return processInstance;
  }
}
