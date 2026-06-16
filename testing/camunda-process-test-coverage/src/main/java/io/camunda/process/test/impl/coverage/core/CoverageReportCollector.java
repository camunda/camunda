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
package io.camunda.process.test.impl.coverage.core;

import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.process.test.api.coverage.model.CoverageRunReport;
import io.camunda.process.test.api.coverage.model.CoverageSuiteReport;
import io.camunda.process.test.api.coverage.model.DecisionCoverage;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageRunReport;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageSuiteReport;
import io.camunda.process.test.api.coverage.model.ProcessCoverage;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.impl.coverage.data.CoverageDecisionInstanceData;
import io.camunda.process.test.impl.coverage.data.CoverageProcessInstanceData;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects process and decision coverage data for test execution.
 *
 * <p>This class is responsible for collecting coverage information from process instances and
 * decision instances, organizing it into test runs within a suite, and maintaining model
 * information for the processes and decisions being tested.
 */
public final class CoverageReportCollector {
  private static final Logger LOG = LoggerFactory.getLogger(CoverageReportCollector.class);

  private final List<String> excludedProcessDefinitionIds;
  private final List<String> excludedDecisionDefinitionIds;
  private final Map<String, ProcessModel> models = new HashMap<>();
  private final Map<String, DecisionModel> decisionModels = new HashMap<>();
  private final List<CoverageRunReport> coverageRunReports = new ArrayList<>();

  private final String suiteId;
  private final String suiteName;

  public CoverageReportCollector(
      final Class<?> testClass,
      final List<String> excludedProcessDefinitionIds,
      final List<String> excludedDecisionDefinitionIds) {
    suiteId = testClass.getName();
    suiteName = extractQualifiedClassName(testClass);
    this.excludedProcessDefinitionIds = excludedProcessDefinitionIds;
    this.excludedDecisionDefinitionIds = excludedDecisionDefinitionIds;
  }

  /**
   * Collects coverage data for a specific test run.
   *
   * <p>Retrieves process instances from the data source, filters out excluded processes, creates
   * coverage data for each instance, and adds the collected data to the suite. Also collects
   * decision table coverage from decision instances.
   *
   * @param runName Identifier for the current test run (the test method name)
   * @param displayName Optional custom display name for the test case (e.g. from
   *     {@code @DisplayName}), or {@code null} if not set
   */
  public void collectTestRunCoverage(
      final String runName, final String displayName, final CoverageTestData testResults) {
    final List<CoverageProcessInstanceData> filteredProcessInstanceData =
        testResults.getProcessInstanceData().stream()
            .filter(
                processInstanceData ->
                    !excludedProcessDefinitionIds.contains(
                        processInstanceData.getProcessInstance().getProcessDefinitionId()))
            .collect(Collectors.toList());

    final List<ProcessCoverage> coverages =
        filteredProcessInstanceData.stream()
            .map(
                processInstanceResult ->
                    CoverageCreator.createCoverage(
                        processInstanceResult,
                        models.computeIfAbsent(
                            processInstanceResult.getProcessInstance().getProcessDefinitionId(),
                            key -> ModelCreator.createModel(testResults, key))))
            .collect(Collectors.toList());

    final List<DecisionCoverage> decisionCoverages = collectDecisionCoverages(testResults);

    coverageRunReports.add(
        ImmutableCoverageRunReport.builder()
            .name(extractMethodRunName(runName))
            .displayName(displayName)
            .addAllProcessCoverages(coverages)
            .addAllDecisionCoverages(decisionCoverages)
            .build());
  }

  /**
   * Gets the test suite containing all collected coverage data.
   *
   * @return The test suite with coverage information
   */
  public CoverageSuiteReport getSuite() {
    return ImmutableCoverageSuiteReport.builder()
        .id(suiteId)
        .name(suiteName)
        .addAllRuns(coverageRunReports)
        .build();
  }

  /**
   * Gets all process models for which coverage has been collected.
   *
   * @return Collection of process models
   */
  public Collection<ProcessModel> getModels() {
    return models.values();
  }

  /**
   * Gets all decision models for which coverage has been collected.
   *
   * @return Collection of decision models
   */
  public Collection<DecisionModel> getDecisionModels() {
    return decisionModels.values();
  }

  private List<DecisionCoverage> collectDecisionCoverages(final CoverageTestData dataSource) {
    final List<CoverageDecisionInstanceData> filteredDecisionInstanceData =
        dataSource.getDecisionInstanceData().stream()
            .filter(
                decisionInstanceData ->
                    decisionInstanceData.getDecisionInstance().getDecisionDefinitionType()
                        == DecisionDefinitionType.DECISION_TABLE)
            .filter(
                decisionInstanceResult ->
                    !excludedDecisionDefinitionIds.contains(
                        decisionInstanceResult.getDecisionInstance().getDecisionDefinitionId()))
            .collect(Collectors.toList());

    return filteredDecisionInstanceData.stream()
        .map(
            decisionInstanceResult -> {
              try {
                return DecisionCoverageCreator.createCoverage(
                    decisionInstanceResult,
                    decisionModels.computeIfAbsent(
                        decisionInstanceResult.getDecisionInstance().getDecisionDefinitionId(),
                        key -> DecisionModelCreator.createModel(dataSource, key)));
              } catch (final Exception e) {
                LOG.warn(
                    "Failed to collect coverage for decision '{}': {}",
                    decisionInstanceResult.getDecisionInstance().getDecisionDefinitionId(),
                    e.getMessage());
                return null;
              }
            })
        .filter(dc -> dc != null)
        .collect(Collectors.toList());
  }

  private static String extractQualifiedClassName(final Class<?> testClass) {
    final String className = testClass.getName();
    final int packageSeparatorIndex = className.lastIndexOf('.');
    if (packageSeparatorIndex < 0) {
      return className;
    }
    return className.substring(packageSeparatorIndex + 1);
  }

  private static String extractMethodRunName(final String runName) {
    final int separatorIndex = runName.lastIndexOf('#');
    if (separatorIndex < 0) {
      return runName;
    }
    return runName.substring(separatorIndex + 1);
  }
}
