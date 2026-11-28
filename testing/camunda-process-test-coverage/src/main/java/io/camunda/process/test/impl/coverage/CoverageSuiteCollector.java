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
package io.camunda.process.test.impl.coverage;

import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.impl.coverage.core.CoverageReportCollector;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import io.camunda.process.test.impl.coverage.report.CoverageReporter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Collects the coverage of test runs per suite and writes the coverage report.
 *
 * <p>An instance owns the suites it collected, and the report contains exactly these suites. To
 * report the coverage of several suites together, collect them with the same instance.
 */
public final class CoverageSuiteCollector {

  private final Map<String, CoverageReportCollector> collectorsBySuiteId = new HashMap<>();

  private final List<String> excludedProcessDefinitionIds;
  private final List<String> excludedDecisionDefinitionIds;

  private final CoverageReporter coverageReporter;

  public CoverageSuiteCollector(
      final List<String> excludedProcessDefinitionIds,
      final List<String> excludedDecisionDefinitionIds,
      final String reportDirectory,
      final Consumer<String> printStream) {
    this.excludedProcessDefinitionIds = excludedProcessDefinitionIds;
    this.excludedDecisionDefinitionIds = excludedDecisionDefinitionIds;

    coverageReporter = new CoverageReporter(reportDirectory, printStream);
  }

  /**
   * Collects coverage data for one test run and returns the suite-level coverage report.
   *
   * @param suiteId the id of the suite the test run belongs to
   * @param suiteName the name of the suite, for display in the report
   * @param runName name of the test run for identification in reports
   * @param displayName optional custom display name, or {@code null} if not set
   * @param testData snapshot of all test run data required for coverage calculation
   * @return coverage report for the suite
   */
  public synchronized CoverageReport collectTestRunCoverage(
      final String suiteId,
      final String suiteName,
      final String runName,
      final String displayName,
      final CoverageTestData testData) {

    final CoverageReportCollector coverageReportCollector =
        collectorsBySuiteId.computeIfAbsent(
            suiteId,
            id ->
                new CoverageReportCollector(
                    id, suiteName, excludedProcessDefinitionIds, excludedDecisionDefinitionIds));

    coverageReportCollector.collectTestRunCoverage(runName, displayName, testData);
    return coverageReporter.createSuiteCoverageReport(coverageReportCollector);
  }

  /**
   * Returns the aggregated report of all suites without writing it.
   *
   * @return aggregated coverage report
   */
  public synchronized CoverageReport getAggregatedReport() {
    return coverageReporter.aggregateReports(collectorsBySuiteId.values());
  }

  /**
   * Generates coverage reports (JSON/HTML), prints the coverage summary of the suite, and returns
   * the aggregated report of all suites.
   *
   * @param suiteId the id of the suite to print the summary for
   * @return aggregated coverage report
   */
  public synchronized CoverageReport generateReport(final String suiteId) {
    Optional.ofNullable(collectorsBySuiteId.get(suiteId))
        .ifPresent(coverageReporter::printCoverage);

    return coverageReporter.createAggregatedReport(collectorsBySuiteId.values());
  }
}
