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
import java.util.Collection;
import java.util.Collections;
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
  public static SuiteCoverageReport createSuiteCoverageReport(
      final Suite suite, final Collection<Model> models) {
    final Collection<Coverage> coverages =
        CoverageCreator.aggregateCoverages(allCoverages(Collections.singletonList(suite)), models);
    return new SuiteCoverageReport(
        suite.getId(), suite.getName(), suite.getRuns(), models, coverages);
  }

  /**
   * Creates a coverage report from multiple test suites and associated process models.
   *
   * @param suites Collection of test suites containing coverage information
   * @param models Collection of process models with structure information
   * @return A CoverageReport containing all suites, models and aggregated coverage data
   */
  public static AggregatedCoverageReport createAggregatedCoverageReport(
      final Collection<Suite> suites, final Collection<Model> models) {
    final Collection<Coverage> coverages =
        CoverageCreator.aggregateCoverages(allCoverages(suites), models);
    final Collection<AggregatedSuiteInfo> suiteInfos =
        suites.stream()
            .map(
                suite ->
                    new AggregatedSuiteInfo(
                        suite.getId(),
                        suite.getName(),
                        CoverageCreator.aggregateCoverages(
                            allCoverages(Collections.singletonList(suite)), models)))
            .collect(Collectors.toList());
    return new AggregatedCoverageReport(suiteInfos, models, coverages);
  }

  /**
   * Creates an HTML coverage report from multiple test suites and their associated process models.
   *
   * <p>This method: 1. Generates individual suite coverage reports for each provided test suite
   * 2.Aggregates coverage data from all suites 3. Collects process definitions from models for
   * visualization 4. Creates a unified HTML report containing all coverage data
   *
   * @param suites Collection of test suites containing execution data and coverage information
   * @param models Collection of process models with structure information for coverage calculation
   * @return A complete HTML coverage report object containing suite reports, aggregated coverage
   *     data, and process definitions
   */
  public static HtmlCoverageReport createHtmlCoverageReport(
      final Collection<Suite> suites, final Collection<Model> models) {
    final Collection<SuiteCoverageReport> suiteReports =
        suites.stream()
            .map(suite -> createSuiteCoverageReport(suite, models))
            .collect(Collectors.toList());
    final Collection<Coverage> coverages =
        CoverageCreator.aggregateCoverages(allCoverages(suites), models);
    final Map<String, String> definitions =
        models.stream()
            .collect(Collectors.toMap(Model::getProcessDefinitionId, Model::xml, (a, b) -> a));
    return new HtmlCoverageReport(suiteReports, coverages, definitions);
  }

  /**
   * Extracts and flattens all coverage entries from a collection of test suites.
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
