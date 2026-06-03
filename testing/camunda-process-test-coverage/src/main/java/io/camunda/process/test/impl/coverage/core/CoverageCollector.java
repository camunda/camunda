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
import io.camunda.process.test.api.coverage.model.DecisionCoverage;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableRun;
import io.camunda.process.test.api.coverage.model.ImmutableSuite;
import io.camunda.process.test.api.coverage.model.ProcessCoverage;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.api.coverage.model.Suite;
import io.camunda.process.test.impl.coverage.data.CoverageDecisionInstanceData;
import io.camunda.process.test.impl.coverage.data.CoverageProcessInstanceData;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
public final class CoverageCollector {
  private static final Logger LOG = LoggerFactory.getLogger(CoverageCollector.class);
  private static final List<CoverageCollector> COLLECTORS = new ArrayList<>();
  private final List<String> excludedProcessDefinitionIds;
  private final List<String> excludedDecisionDefinitionIds;
  private final String suiteId;
  private final String suiteName;
  private final Map<String, ProcessModel> models = new HashMap<>();
  private final Map<String, DecisionModel> decisionModels = new HashMap<>();
  private final List<CoverageRunReport> coverageRunReports = new ArrayList<>();

  private CoverageCollector(
      final Class<?> testClass,
      final List<String> excludedProcessDefinitionIds,
      final List<String> excludedDecisionDefinitionIds) {
    this.excludedProcessDefinitionIds = excludedProcessDefinitionIds;
    this.excludedDecisionDefinitionIds = excludedDecisionDefinitionIds;
    suiteId = testClass.getName();
    suiteName = testClass.getSimpleName();
  }

  /**
   * Creates a new coverage collector for a specific test class.
   *
   * @param testClass The class for which coverage is being collected
   * @param excludedProcessDefinitionIds List of process definition ids to exclude from coverage
   *     analysis
   * @param excludedDecisionDefinitionIds List of decision definition ids to exclude from coverage
   *     analysis
   * @param dataSourceSupplier Supplier for the Camunda data source used to access process data
   */
  public static CoverageCollector createCollector(
      final Class<?> testClass,
      final List<String> excludedProcessDefinitionIds,
      final List<String> excludedDecisionDefinitionIds) {
    final CoverageCollector collector =
        new CoverageCollector(
            testClass, excludedProcessDefinitionIds, excludedDecisionDefinitionIds);
    COLLECTORS.add(collector);
    return collector;
  }

  /**
   * Returns all active coverage collectors.
   *
   * <p>This method provides access to all coverage collectors that have been created during the
   * test execution. These collectors contain coverage data for each test suite and can be used to
   * generate aggregated coverage reports across multiple test classes.
   *
   * @return A collection of all active CoverageCollector instances
   */
  public static Collection<CoverageCollector> collectors() {
    return Collections.unmodifiableList(COLLECTORS);
  }

  /**
   * Collects coverage data for a specific test run.
   *
   * <p>Retrieves process instances from the data source, filters out excluded processes, creates
   * coverage data for each instance, and adds the collected data to the suite. Also collects
   * decision table coverage from decision instances.
   *
   * @param runName Identifier for the current test run
   */
  public void collectTestRunCoverage(final String runName, final CoverageTestData testResults) {
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
        ImmutableRun.builder()
            .name(runName)
            .addAllProcessCoverages(coverages)
            .addAllDecisionCoverages(decisionCoverages)
            .build());
  }

  /**
   * Gets the test suite containing all collected coverage data.
   *
   * @return The test suite with coverage information
   */
  public Suite getSuite() {
    return ImmutableSuite.builder()
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
}
