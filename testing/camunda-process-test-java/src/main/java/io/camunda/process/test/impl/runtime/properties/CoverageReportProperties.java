/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime.properties;

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyListOrEmpty;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;

import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.util.List;
import java.util.Properties;

public class CoverageReportProperties {

  public static final String PROPERTY_NAME_COVERAGE_REPORT_DIRECTORY = "coverage.reportDirectory";
  public static final String PROPERTY_NAME_COVERAGE_REPORT_EXCLUDED_PROCESSES =
      "coverage.excludedProcesses";

  private final String coverageReportDirectory;
  private final List<String> coverageExcludedProcesses;

  public CoverageReportProperties(final Properties properties) {
    coverageReportDirectory =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_COVERAGE_REPORT_DIRECTORY,
            CamundaProcessTestRuntimeDefaults.DEFAULT_COVERAGE_REPORT_DIRECTORY);

    coverageExcludedProcesses =
        getPropertyListOrEmpty(properties, PROPERTY_NAME_COVERAGE_REPORT_EXCLUDED_PROCESSES);
  }

  public String getCoverageReportDirectory() {
    return coverageReportDirectory;
  }

  public List<String> getCoverageExcludedProcesses() {
    return coverageExcludedProcesses;
  }
}
