/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.extension;

import io.camunda.process.test.impl.assertions.AssertFormatUtil;
import io.camunda.process.test.impl.testresult.ProcessInstanceResult;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CamundaProcessTestResultPrinter {

  private final Consumer<String> printStream;

  public CamundaProcessTestResultPrinter(final Consumer<String> printStream) {
    this.printStream = printStream;
  }

  public void print(final ProcessTestResult result) {
    final String formattedResult = format(result);
    printStream.accept(formattedResult);
  }

  private String format(final ProcessTestResult result) {
    final String formattedResults =
        result.getProcessInstanceTestResults().stream()
            .map(this::format)
            .collect(Collectors.joining("\n---------------------\n\n"));
    return "Process test results:\n"
        + "=====================\n\n"
        + formattedResults
        + "\n"
        + "=====================\n";
  }

  private String format(final ProcessInstanceResult result) {
    String formattedVariables = "<None>";
    if (!result.getVariables().isEmpty()) {
      formattedVariables = AssertFormatUtil.formatVariables(result.getVariables());
    }
    return String.format(
        "Process instance: %d [process-id: '%s']\n\nVariables:\n%s",
        result.getProcessInstanceKey(), result.getProcessId(), formattedVariables);
  }
}
