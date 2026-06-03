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
import io.camunda.process.test.api.coverage.model.CoverageSuiteReport;
import io.camunda.process.test.api.coverage.model.DecisionCoverage;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageReport;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageSuiteReport;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.api.coverage.model.ProcessCoverage;
import io.camunda.process.test.api.coverage.model.Suite;
import io.camunda.process.test.impl.coverage.core.CoverageCreator;
import io.camunda.process.test.impl.coverage.core.DecisionCoverageCreator;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/** Utility class for creating and aggregating coverage reports. */
public class CoverageReportCreator {
  public static CoverageSuiteReport createSuiteCoverageReport(
      final Suite suite,
      final Collection<Model> models,
      final Collection<DecisionModel> decisionModels) {
    final java.util.List<ProcessCoverage> processCoverages =
        CoverageCreator.aggregateCoverages(
            allProcessCoverages(Collections.singletonList(suite)), models);
    final java.util.List<DecisionCoverage> decisionCoverages =
        DecisionCoverageCreator.aggregateCoverages(
            allDecisionCoverages(Collections.singletonList(suite)), decisionModels);
    return ImmutableCoverageSuiteReport.builder()
        .id(suite.getId())
        .name(suite.getName())
        .addAllRuns(suite.getRuns())
        .addAllProcessCoverages(processCoverages)
        .addAllDecisionCoverages(decisionCoverages)
        .build();
  }

  public static CoverageReport createAggregatedCoverageReport(
      final Collection<Suite> suites,
      final Collection<Model> models,
      final Collection<DecisionModel> decisionModels) {
    final java.util.List<CoverageSuiteReport> suiteReports =
        suites.stream()
            .map(suite -> createSuiteCoverageReport(suite, models, decisionModels))
            .collect(Collectors.toList());
    final java.util.List<ProcessCoverage> processCoverages =
        CoverageCreator.aggregateCoverages(allProcessCoverages(suites), models);
    final java.util.List<DecisionCoverage> decisionCoverages =
        DecisionCoverageCreator.aggregateCoverages(allDecisionCoverages(suites), decisionModels);
    final Map<String, String> definitions =
        models.stream()
            .collect(Collectors.toMap(Model::getProcessDefinitionId, Model::xml, (a, b) -> a));
    final Map<String, String> decisionDefinitions =
        decisionModels.stream()
            .collect(
                Collectors.toMap(
                    DecisionModel::getDecisionDefinitionId, DecisionModel::xml, (a, b) -> a));
    return ImmutableCoverageReport.builder()
        .addAllSuites(suiteReports)
        .addAllModels(models)
        .addAllDecisionModels(decisionModels)
        .addAllProcessCoverages(processCoverages)
        .addAllDecisionCoverages(decisionCoverages)
        .putAllDefinitions(definitions)
        .putAllDecisionDefinitions(decisionDefinitions)
        .build();
  }

  /**
   * Extracts and flattens all coverage entries from a collection of test suites.
   *
   * @param suites Collection of test suites to extract coverage data from
   * @return A flat collection of all ProcessCoverage objects from all test runs
   */
  private static Collection<ProcessCoverage> allProcessCoverages(final Collection<Suite> suites) {
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
  private static Collection<DecisionCoverage> allDecisionCoverages(final Collection<Suite> suites) {
    return suites.stream()
        .flatMap(suite -> suite.getRuns().stream().flatMap(r -> r.getDecisionCoverages().stream()))
        .collect(Collectors.toList());
  }
}
