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

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.impl.coverage.CoverageCollectorBuilderImpl;
import java.time.Instant;

/**
 * Collects the process and decision coverage of test runs and reports it.
 *
 * <p>The collector reads the coverage of a test run from the data of the cluster. Only the data
 * that is created since the start of the test run is attributed to it.
 *
 * <p>A collector owns the coverage of the test runs it collected, and the report contains exactly
 * these. To report the coverage of several suites together, collect them with the same collector.
 * The collector is thread-safe.
 */
public interface CoverageCollector {

  /**
   * Creates a builder to configure and create a collector.
   *
   * @return a new builder
   */
  static CoverageCollectorBuilder newBuilder() {
    return new CoverageCollectorBuilderImpl();
  }

  /**
   * Collects the coverage of a test run. It reads the process instances and decision instances of
   * the test run from the cluster.
   *
   * @param testRun the identifier of the test run, including the suite it belongs to
   * @param client the client to read the data from the cluster
   * @param testCaseStartTime the start time of the test run to ignore the data that was created
   *     before, or {@code null} to include all data of the cluster
   * @return the coverage report of the suite of the test run
   */
  CoverageReport collectTestRunCoverage(
      CoverageTestIdentifier testRun, CamundaClient client, Instant testCaseStartTime);

  /**
   * Returns the coverage report of all suites, without printing a summary or writing report files.
   *
   * @return the coverage report of all suites
   */
  CoverageReport getAggregatedReport();

  /**
   * Prints the coverage summary of the suite, writes the report files (JSON and HTML) and returns
   * the report. The report contains all suites.
   *
   * @param suiteId the id of the suite to print the summary for. See {@link
   *     CoverageTestIdentifier#getSuiteId()}
   * @return the coverage report of all suites
   */
  CoverageReport generateReport(String suiteId);
}
