/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.configuration;

import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.util.Collections;
import java.util.List;

public class CoverageReportConfiguration {

  private String reportDirectory =
      CamundaProcessTestRuntimeDefaults.DEFAULT_COVERAGE_REPORT_DIRECTORY;
  private List<String> excludedProcesses = Collections.emptyList();

  public String getReportDirectory() {
    return reportDirectory;
  }

  public void setReportDirectory(final String reportDirectory) {
    this.reportDirectory = reportDirectory;
  }

  public List<String> getExcludedProcesses() {
    return excludedProcesses;
  }

  public void setExcludedProcesses(final List<String> excludedProcesses) {
    this.excludedProcesses = excludedProcesses;
  }
}
