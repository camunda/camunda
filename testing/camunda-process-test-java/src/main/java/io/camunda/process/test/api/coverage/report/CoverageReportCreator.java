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
package io.camunda.process.test.api.coverage.report;

import io.camunda.process.test.api.coverage.core.CoverageCreator;
import io.camunda.process.test.api.coverage.model.Coverage;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.api.coverage.model.Suite;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Utility class for creating and aggregating coverage reports. */
public class CoverageReportCreator {
  /**
   * Creates a coverage report from a single test suite and associated process models.
   *
   * @param suite The test suite containing coverage information
   * @param models Collection of process models with structure information
   * @return A CoverageReport containing the suite, models and aggregated coverage data
   */
  public static CoverageReport create(final Suite suite, final Collection<Model> models) {
    return create(Collections.singletonList(suite), models);
  }

  /**
   * Creates a coverage report from multiple test suites and associated process models.
   *
   * @param suites Collection of test suites containing coverage information
   * @param models Collection of process models with structure information
   * @return A CoverageReport containing all suites, models and aggregated coverage data
   */
  public static CoverageReport create(
      final Collection<Suite> suites, final Collection<Model> models) {
    final Collection<Coverage> coverages =
        CoverageCreator.aggregateCoverages(allCoverages(suites), models);
    return new CoverageReport(suites, models, coverages);
  }

  /**
   * Merges an existing coverage report with a new one, preserving data from both.
   *
   * <p>This method combines suites and models from both reports, prioritizing newer data when there
   * are conflicts. Coverages are aggregated based on the merged data.
   *
   * @param oldReport The existing coverage report to merge from
   * @param newReport The new coverage report to merge with
   * @return A new CoverageReport containing the merged and aggregated data
   */
  public static CoverageReport aggregatedReport(
      final CoverageReport oldReport, final CoverageReport newReport) {
    // Merge models, replacing with new ones (by id) if they exist
    final Map<String, Suite> suiteMap = new HashMap<>();
    oldReport.getSuites().forEach(suite -> suiteMap.put(suite.getId(), suite));
    newReport.getSuites().forEach(suite -> suiteMap.put(suite.getId(), suite));
    final List<Suite> mergedSuites = new ArrayList<>(suiteMap.values());

    // Merge models, replacing with new ones (by processDefinitionId) if they exist
    final Map<String, Model> modelMap = new HashMap<>();
    oldReport.getModels().forEach(model -> modelMap.put(model.getProcessDefinitionId(), model));
    newReport.getModels().forEach(model -> modelMap.put(model.getProcessDefinitionId(), model));

    // Collect and aggregate all coverages
    final Collection<Coverage> allCoverages = allCoverages(suiteMap.values());
    final Collection<Coverage> aggregatedCoverages =
        CoverageCreator.aggregateCoverages(allCoverages, modelMap.values());

    return new CoverageReport(mergedSuites, modelMap.values(), aggregatedCoverages);
  }

  /**
   * Extracts amd flattens all coverage entries from a collection of test suites.
   *
   * @param suites Collection of test suites to extract coverage data from
   * @return A flat collection of all Coverage objects from all test runs
   */
  private static Collection<Coverage> allCoverages(final Collection<Suite> suites) {
    return suites.stream()
        .flatMap(suite -> suite.getRuns().stream().flatMap(r -> r.getCoverages().stream()))
        .collect(Collectors.toList());
  }
}
