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
package io.camunda.process.test.api.coverage.core;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.coverage.model.Coverage;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.api.coverage.model.Run;
import io.camunda.process.test.api.coverage.model.Suite;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Collects process coverage data for test execution.
 *
 * <p>This class is responsible for collecting coverage information from process instances,
 * organizing it into test runs within a suite, and maintaining model information for the processes
 * being tested.
 */
public final class CoverageCollector {
  private static final List<CoverageCollector> COLLECTORS = new ArrayList<>();
  private final List<String> excludedProcessDefinitionIds;
  private final Supplier<CamundaDataSource> dataSourceSupplier;
  private final Map<String, Model> models = new HashMap<>();
  private final Suite suite;

  private CoverageCollector(
      final Class<?> testClass,
      final List<String> excludedProcessDefinitionIds,
      final Supplier<CamundaDataSource> dataSourceSupplier) {
    this.excludedProcessDefinitionIds = excludedProcessDefinitionIds;
    this.dataSourceSupplier = dataSourceSupplier;
    suite = new Suite(testClass.getName(), testClass.getSimpleName());
  }

  /**
   * Creates a new coverage collector for a specific test class.
   *
   * @param testClass The class for which coverage is being collected
   * @param excludedProcessDefinitionIds List of process definition ids to exclude from coverage
   *     analysis
   * @param dataSourceSupplier Supplier for the Camunda data source used to access process data
   */
  public static CoverageCollector createCollector(
      final Class<?> testClass,
      final List<String> excludedProcessDefinitionIds,
      final Supplier<CamundaDataSource> dataSourceSupplier) {
    final CoverageCollector collector =
        new CoverageCollector(testClass, excludedProcessDefinitionIds, dataSourceSupplier);
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
   * coverage data for each instance, and adds the collected data to the suite.
   *
   * @param runName Identifier for the current test run
   */
  public void collectTestRunCoverage(final String runName) {
    final CamundaDataSource dataSource = dataSourceSupplier.get();
    final List<ProcessInstance> processInstances =
        dataSource.findProcessInstances().stream()
            .filter(
                processInstance ->
                    !excludedProcessDefinitionIds.contains(
                        processInstance.getProcessDefinitionId()))
            .collect(Collectors.toList());

    final List<Coverage> coverages =
        processInstances.stream()
            .map(
                processInstance ->
                    CoverageCreator.createCoverage(
                        dataSource,
                        processInstance,
                        models.computeIfAbsent(
                            processInstance.getProcessDefinitionId(),
                            key -> ModelCreator.createModel(dataSource, key))))
            .collect(Collectors.toList());
    suite.addRun(new Run(runName, coverages));
  }

  /**
   * Gets the test suite containing all collected coverage data.
   *
   * @return The test suite with coverage information
   */
  public Suite getSuite() {
    return suite;
  }

  /**
   * Gets all process models for which coverage has been collected.
   *
   * @return Collection of process models
   */
  public Collection<Model> getModels() {
    return models.values();
  }
}
