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

import io.camunda.process.test.api.coverage.CoverageDataSource;
import io.camunda.process.test.api.coverage.ProcessCoverage;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.impl.coverage.core.CoverageCollector;
import io.camunda.process.test.impl.coverage.report.CoverageReporter;
import java.util.List;
import java.util.function.Consumer;

public final class DefaultProcessCoverage implements ProcessCoverage {

  private final CoverageCollector coverageCollector;
  private final CoverageReporter coverageReporter;

  public DefaultProcessCoverage(
      final Class<?> testClass,
      final List<String> excludedProcessDefinitionIds,
      final List<String> excludedDecisionDefinitionIds,
      final String reportDirectory,
      final Consumer<String> printStream) {
    coverageCollector =
        CoverageCollector.createCollector(
            testClass, excludedProcessDefinitionIds, excludedDecisionDefinitionIds);
    coverageReporter = new CoverageReporter(reportDirectory, printStream);
  }

  @Override
  public CoverageReport collectTestRunCoverage(
      final String runName, final CoverageDataSource dataSource) {
    coverageCollector.collectTestRunCoverage(runName, dataSource);
    return coverageReporter.createSuiteCoverageReport(coverageCollector);
  }

  @Override
  public CoverageReport reportCoverage() {
    return coverageReporter.reportCoverage(coverageCollector);
  }
}
