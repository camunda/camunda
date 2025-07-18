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
package io.camunda.process.test.api.coverage;

import io.camunda.process.test.api.coverage.core.CoverageCollector;
import io.camunda.process.test.api.coverage.report.CoverageReporter;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages process coverage collection and reporting for Camunda processes.
 *
 * <p>This class provides functionality to collect coverage data during test runs and generate
 * reports showing the process coverage metrics.
 */
public class ProcessCoverage {

  private final CoverageCollector coverageCollector;
  private final CoverageReporter coverageReporter;

  /**
   * Creates a new ProcessCoverage instance.
   *
   * @param testClass The test class being executed
   * @param excludedProcessDefinitionIds List of process definition ids to exclude from coverage
   *     analysis
   * @param reportDirectory Directory where the coverage reports will be generated
   * @param dataSourceSupplier Supplier for the Camunda data source used to access process data
   */
  public ProcessCoverage(
      final Class<?> testClass,
      final List<String> excludedProcessDefinitionIds,
      final String reportDirectory,
      final Consumer<String> printStream,
      final Supplier<CamundaDataSource> dataSourceSupplier) {
    coverageCollector =
        new CoverageCollector(testClass, excludedProcessDefinitionIds, dataSourceSupplier);
    coverageReporter = new CoverageReporter(reportDirectory, printStream);
  }

  /**
   * Creates a new builder for configuring and creating ProcessCoverage instances.
   *
   * @return A new ProcessCoverageBuilder instance
   */
  public static ProcessCoverageBuilder newBuilder() {
    return new ProcessCoverageBuilder();
  }

  /**
   * Collects coverage data for the current test run.
   *
   * @param runName Name of the test run for identification in reports
   */
  public void collectTestRunCoverage(final String runName) {
    coverageCollector.collectTestRunCoverage(runName);
  }

  /**
   * Generates and outputs coverage reports based on the collected data. The reports are generated
   * in the configured report directory and summary information is printed to standard error.
   */
  public void reportCoverage() {
    coverageReporter.reportCoverage(coverageCollector);
    coverageReporter.printCoverage(coverageCollector);
  }
}
