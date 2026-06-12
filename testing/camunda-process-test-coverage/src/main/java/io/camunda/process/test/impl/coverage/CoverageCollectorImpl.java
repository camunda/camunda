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
package io.camunda.process.test.impl.coverage;

import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.impl.coverage.core.CoverageReportCollector;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import io.camunda.process.test.impl.coverage.report.CoverageReporter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class CoverageCollectorImpl implements CoverageCollector {

  private static final Map<String, CoverageReportCollector> COLLECTORS_BY_TEST_CLASS =
      new HashMap<>();

  private final List<String> excludedProcessDefinitionIds;
  private final List<String> excludedDecisionDefinitionIds;

  private final CoverageReporter coverageReporter;

  public CoverageCollectorImpl(
      final List<String> excludedProcessDefinitionIds,
      final List<String> excludedDecisionDefinitionIds,
      final String reportDirectory,
      final Consumer<String> printStream) {
    this.excludedProcessDefinitionIds = excludedProcessDefinitionIds;
    this.excludedDecisionDefinitionIds = excludedDecisionDefinitionIds;

    coverageReporter = new CoverageReporter(reportDirectory, printStream);
  }

  @Override
  public CoverageReport collectTestRunCoverage(
      final Class<?> testClass,
      final String runName,
      final String displayName,
      final CoverageTestData testData) {

    final String testClassName = testClass.getName();
    final CoverageReportCollector coverageReportCollector =
        COLLECTORS_BY_TEST_CLASS.computeIfAbsent(
            testClassName,
            name ->
                new CoverageReportCollector(
                    testClass, excludedProcessDefinitionIds, excludedDecisionDefinitionIds));

    coverageReportCollector.collectTestRunCoverage(runName, displayName, testData);
    return coverageReporter.createSuiteCoverageReport(coverageReportCollector);
  }

  @Override
  public CoverageReport generateReport(final Class<?> testClass) {

    final String testClassName = testClass.getName();
    Optional.ofNullable(COLLECTORS_BY_TEST_CLASS.get(testClassName))
        .ifPresent(
            coverageReportCollector -> coverageReporter.printCoverage(coverageReportCollector));

    return coverageReporter.createAggregatedReport(COLLECTORS_BY_TEST_CLASS.values());
  }
}
