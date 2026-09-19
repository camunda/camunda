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
import io.camunda.client.api.search.response.ProcessInstance;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final Set<Long> mockedProcessDefinitionKeys = new LinkedHashSet<>();

  private final String suiteId;
  private final String suiteName;

  public CoverageReportCollector(
      final Class<?> testClass,
      final List<String> excludedProcessDefinitionIds,
      final List<String> excludedDecisionDefinitionIds) {
    suiteId = extractCollectorClassName(testClass);
    suiteName = extractSuiteName(testClass);
    this.excludedProcessDefinitionIds = excludedProcessDefinitionIds;
    this.excludedDecisionDefinitionIds = excludedDecisionDefinitionIds;
  }

  /**
   * Collects coverage data for a specific test run.
   *
   * <p>Retrieves process instances from the data source, filters out excluded and mocked processes,
   * creates coverage data for each instance, and adds the collected data to the suite. Also
   * collects decision table coverage from decision instances.
   *
   * @param runName Identifier for the current test run (the test method name)
   * @param displayName Optional custom display name for the test case (e.g. from
   *     {@code @DisplayName}), or {@code null} if not set
   * @param mockedProcessDefinitionKeys Keys of the process definitions that this run deployed as a
   *     stub of a mocked process, whose instances are stubs rather than the process under test
   */
  public void collectTestRunCoverage(
      final String runName,
      final String displayName,
      final CoverageTestData testResults,
      final Collection<Long> mockedProcessDefinitionKeys) {
    this.mockedProcessDefinitionKeys.addAll(mockedProcessDefinitionKeys);

    final List<CoverageProcessInstanceData> filteredProcessInstanceData =
        testResults.getProcessInstanceData().stream()
            .filter(processInstanceData -> !isExcluded(processInstanceData))
            .collect(Collectors.toList());

    // the models of this run, keyed by the deployment that ran: a test can run several deployments
    // of one process definition id, and they are not necessarily the deployments of previous runs
    final Map<Long, ProcessModel> runModels = new HashMap<>();

    final List<ProcessCoverage> coverages =
        filteredProcessInstanceData.stream()
            .map(
                processInstanceResult ->
                    CoverageCreator.createCoverage(
                        processInstanceResult,
                        runModels.computeIfAbsent(
                            processInstanceResult.getProcessInstance().getProcessDefinitionKey(),
                            processDefinitionKey ->
                                ModelCreator.createModel(
                                    testResults,
                                    processInstanceResult
                                        .getProcessInstance()
                                        .getProcessDefinitionId(),
                                    processDefinitionKey))))
            .collect(Collectors.toList());

    runModels
        .values()
        .forEach(
            runModel ->
                models.merge(
                    runModel.getProcessDefinitionId(),
                    runModel,
                    ModelCreator::selectMostCompleteModel));

    final List<DecisionCoverage> decisionCoverages = collectDecisionCoverages(testResults);

    coverageRunReports.add(
        ImmutableCoverageRunReport.builder()
            .name(runName)
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

  /**
   * Tells whether an instance is coverage of the process under test.
   *
   * <p>An instance of a stub that a mock deployed is not: it is an instance of a few elements
   * standing in for the process, not of the process itself. The stubs of all runs of this suite are
   * recognised, not only those of the current run, because the instances of a previous run are
   * still around when the test data between runs is kept.
   *
   * @param processInstanceData The instance to judge
   * @return {@code true} if the instance must not be counted as coverage
   */
  private boolean isExcluded(final CoverageProcessInstanceData processInstanceData) {
    final ProcessInstance processInstance = processInstanceData.getProcessInstance();
    return excludedProcessDefinitionIds.contains(processInstance.getProcessDefinitionId())
        || mockedProcessDefinitionKeys.contains(processInstance.getProcessDefinitionKey());
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

    // the decision models of this run, keyed by the deployment that was evaluated: a test can
    // evaluate several deployments of one decision definition id, and they are not necessarily the
    // deployments of previous runs
    final Map<Long, DecisionModel> runDecisionModels = new HashMap<>();

    final List<DecisionCoverage> decisionCoverages =
        filteredDecisionInstanceData.stream()
            .map(
                decisionInstanceResult -> {
                  try {
                    return DecisionCoverageCreator.createCoverage(
                        decisionInstanceResult,
                        runDecisionModels.computeIfAbsent(
                            decisionInstanceResult.getDecisionInstance().getDecisionDefinitionKey(),
                            decisionDefinitionKey ->
                                DecisionModelCreator.createModel(
                                    dataSource,
                                    decisionInstanceResult
                                        .getDecisionInstance()
                                        .getDecisionDefinitionId(),
                                    decisionDefinitionKey)));
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

    runDecisionModels
        .values()
        .forEach(
            runDecisionModel ->
                decisionModels.merge(
                    runDecisionModel.getDecisionDefinitionId(),
                    runDecisionModel,
                    DecisionModelCreator::selectMostCompleteModel));

    return decisionCoverages;
  }

  private static String extractCollectorClassName(final Class<?> testClass) {
    final Class<?> enclosingClass = testClass.getEnclosingClass();
    if (enclosingClass != null) {
      return enclosingClass.getName();
    }
    return testClass.getName();
  }

  private static String extractSuiteName(final Class<?> testClass) {
    final Class<?> enclosingClass = testClass.getEnclosingClass();
    if (enclosingClass != null) {
      return enclosingClass.getSimpleName();
    }
    return testClass.getSimpleName();
  }
}
