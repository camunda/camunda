/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testresult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessTestResult {

  private List<ProcessInstanceResult> processInstanceResults = new ArrayList<>();

  public List<ProcessInstanceResult> getProcessInstanceTestResults() {
    return processInstanceResults;
  }

  public void setProcessInstanceTestResults(
      final List<ProcessInstanceResult> processInstanceResults) {
    this.processInstanceResults = processInstanceResults;
  }

  @Override
  public String toString() {
    final String formattedResults =
        processInstanceResults.stream()
            .map(ProcessInstanceResult::toString)
            .collect(Collectors.joining("\n---------------------\n\n"));
    return "Process test results:\n"
        + "=====================\n\n"
        + formattedResults
        + "\n"
        + "=====================\n";
  }
}
