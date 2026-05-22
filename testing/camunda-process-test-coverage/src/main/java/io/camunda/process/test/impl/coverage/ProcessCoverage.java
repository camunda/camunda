/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.coverage;

import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.impl.coverage.results.CoverageTestResults;

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
   * @param testResults Snapshot of all test run data required for coverage calculation
   * @return Coverage report for the current suite
   */
  CoverageReport collectTestRunCoverage(String runName, CoverageTestResults testResults);

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
