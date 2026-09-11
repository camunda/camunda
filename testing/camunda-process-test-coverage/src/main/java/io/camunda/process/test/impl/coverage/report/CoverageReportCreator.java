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
package io.camunda.process.test.impl.coverage.report;

import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.api.coverage.model.CoverageRunReport;
import io.camunda.process.test.api.coverage.model.CoverageSuiteReport;
import io.camunda.process.test.api.coverage.model.DecisionCoverage;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageReport;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageRunReport;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageSuiteReport;
import io.camunda.process.test.api.coverage.model.ProcessCoverage;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.impl.coverage.core.CoverageCreator;
import io.camunda.process.test.impl.coverage.core.DecisionCoverageCreator;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/** Utility class for creating and aggregating coverage reports. */
public class CoverageReportCreator {
  public static CoverageSuiteReport createSuiteCoverageReport(
      final CoverageSuiteReport suite,
      final Collection<ProcessModel> processModels,
      final Collection<DecisionModel> decisionModels) {
    final java.util.List<ProcessCoverage> processCoverages =
        CoverageCreator.aggregateCoverages(
            allProcessCoverages(Collections.singletonList(suite)), processModels);
    final java.util.List<DecisionCoverage> decisionCoverages =
        DecisionCoverageCreator.aggregateCoverages(
            allDecisionCoverages(Collections.singletonList(suite)), decisionModels);
    return ImmutableCoverageSuiteReport.builder()
        .id(suite.getId())
        .name(suite.getName())
        .addAllRuns(
            suite.getRuns().stream()
                .map(run -> createRunCoverageReport(run, processModels, decisionModels))
                .collect(Collectors.toList()))
        .addAllProcessCoverages(processCoverages)
        .addAllDecisionCoverages(decisionCoverages)
        .build();
  }

  /**
   * Measures a test run against the models the report describes its processes and decisions by.
   *
   * <p>A run reports the coverage of the deployments it ran, which are not necessarily the ones the
   * report describes: a process can be deployed both for real and as a mock stub under one id, and
   * suites can deploy tables that differ in their rules under one decision definition id. The
   * report renders a run against the model it selected, so a run left as it was collected would
   * claim a percentage of a model it did not run and highlight elements that the rendered diagram
   * does not contain.
   *
   * <p>Each coverage is measured on its own, so a run that ran a process several times keeps one
   * entry per instance.
   *
   * @param run The run as it was collected
   * @param processModels The models the report describes the processes by
   * @param decisionModels The tables the report describes the decisions by
   * @return The run, its coverages measured against those models
   */
  private static CoverageRunReport createRunCoverageReport(
      final CoverageRunReport run,
      final Collection<ProcessModel> processModels,
      final Collection<DecisionModel> decisionModels) {
    return ImmutableCoverageRunReport.builder()
        .name(run.getName())
        .displayName(run.getDisplayName())
        .addAllProcessCoverages(
            run.getProcessCoverages().stream()
                .map(
                    coverage ->
                        CoverageCreator.measureAgainstReportedModel(coverage, processModels))
                .collect(Collectors.toList()))
        .addAllDecisionCoverages(
            run.getDecisionCoverages().stream()
                .map(
                    coverage ->
                        DecisionCoverageCreator.measureAgainstReportedModel(
                            coverage, decisionModels))
                .collect(Collectors.toList()))
        .build();
  }

  public static CoverageReport createAggregatedCoverageReport(
      final Collection<CoverageSuiteReport> suites,
      final Collection<ProcessModel> processModels,
      final Collection<DecisionModel> decisionModels) {
    final java.util.List<CoverageSuiteReport> suiteReports =
        suites.stream()
            .map(suite -> createSuiteCoverageReport(suite, processModels, decisionModels))
            .collect(Collectors.toList());
    final java.util.List<ProcessCoverage> processCoverages =
        CoverageCreator.aggregateCoverages(allProcessCoverages(suites), processModels);
    final java.util.List<DecisionCoverage> decisionCoverages =
        DecisionCoverageCreator.aggregateCoverages(allDecisionCoverages(suites), decisionModels);
    return ImmutableCoverageReport.builder()
        .addAllSuites(suiteReports)
        .addAllProcessModels(processModels)
        .addAllDecisionModels(decisionModels)
        .addAllProcessCoverages(processCoverages)
        .addAllDecisionCoverages(decisionCoverages)
        .build();
  }

  /**
   * Extracts and flattens all coverage entries from a collection of test suites.
   *
   * @param suites Collection of test suites to extract coverage data from
   * @return A flat collection of all ProcessCoverage objects from all test runs
   */
  private static Collection<ProcessCoverage> allProcessCoverages(
      final Collection<CoverageSuiteReport> suites) {
    return suites.stream()
        .flatMap(suite -> suite.getRuns().stream().flatMap(r -> r.getProcessCoverages().stream()))
        .collect(Collectors.toList());
  }

  /**
   * Extracts and flattens all decision coverage entries from a collection of test suites.
   *
   * @param suites Collection of test suites to extract decision coverage data from
   * @return A flat collection of all DecisionCoverage objects from all test runs
   */
  private static Collection<DecisionCoverage> allDecisionCoverages(
      final Collection<CoverageSuiteReport> suites) {
    return suites.stream()
        .flatMap(suite -> suite.getRuns().stream().flatMap(r -> r.getDecisionCoverages().stream()))
        .collect(Collectors.toList());
  }
}
