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

import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.impl.coverage.DefaultProcessCoverageBuilder;

/**
 * API for process coverage collection and report generation.
 *
 * <p>Use {@link #newBuilder()} to create an instance.
 */
public interface ProcessCoverage {

  /**
   * Collects coverage data for one test run and returns the suite-level coverage report.
   *
   * @param runName Name of the test run for identification in reports
   * @param dataSource Snapshot of all test run data required for coverage calculation
   * @return Coverage report for the current suite
   */
  CoverageReport collectTestRunCoverage(String runName, CoverageDataSource dataSource);

  /**
   * Generates coverage reports (JSON/HTML), prints coverage summary, and returns the aggregated
   * report.
   *
   * @return Aggregated coverage report
   */
  CoverageReport reportCoverage();

  /**
   * Creates a new builder for configuring and creating ProcessCoverage instances.
   *
   * @return A new ProcessCoverageBuilder instance
   */
  static ProcessCoverageBuilder newBuilder() {
    return new DefaultProcessCoverageBuilder();
  }
}
