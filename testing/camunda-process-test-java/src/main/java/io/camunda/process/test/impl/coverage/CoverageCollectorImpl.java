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

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.coverage.CoverageCollector;
import io.camunda.process.test.api.coverage.CoverageTestIdentifier;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import java.time.Instant;

public final class CoverageCollectorImpl implements CoverageCollector {

  private final CoverageSuiteCollector suiteCollector;
  private final int queryPageLimit;

  public CoverageCollectorImpl(
      final CoverageSuiteCollector suiteCollector, final int queryPageLimit) {
    this.suiteCollector = suiteCollector;
    this.queryPageLimit = queryPageLimit;
  }

  @Override
  public CoverageReport collectTestRunCoverage(
      final CoverageTestIdentifier testRun,
      final CamundaClient client,
      final Instant testCaseStartTime) {

    final CoverageTestData testData =
        CoverageTestDataCollector.collectData(
            new CamundaDataSource(client, testCaseStartTime, queryPageLimit));

    return suiteCollector.collectTestRunCoverage(
        testRun.getSuiteId(),
        testRun.getSuiteName(),
        testRun.getRunName(),
        testRun.getDisplayName(),
        testData);
  }

  @Override
  public CoverageReport getAggregatedReport() {
    return suiteCollector.getAggregatedReport();
  }

  @Override
  public CoverageReport generateReport(final String suiteId) {
    return suiteCollector.generateReport(suiteId);
  }
}
