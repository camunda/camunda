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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.coverage.core.CoverageReportCollector;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverageReporterTest {

  @TempDir Path tempDir;

  @Test
  void shouldNotWriteJsonSuiteReport() {
    // given
    final CoverageReporter reporter = new CoverageReporter(tempDir.toString(), null);
    final CoverageReportCollector collector =
        new CoverageReportCollector(
            TestSuite.class, Collections.emptyList(), Collections.emptyList());

    // when
    reporter.reportSuiteCoverage(collector);

    // then
    assertThat(tempDir.resolve(TestSuite.class.getName()).resolve("report.json")).doesNotExist();
  }

  @Test
  void shouldWriteSingleAggregatedJsonReport() {
    // given
    final CoverageReporter reporter = new CoverageReporter(tempDir.toString(), null);
    final CoverageReportCollector collector =
        new CoverageReportCollector(
            TestSuite.class, Collections.emptyList(), Collections.emptyList());

    // when
    reporter.createAggregatedReport(Collections.singletonList(collector));

    // then
    assertThat(tempDir.resolve("report.json")).exists();
    assertThat(tempDir.resolve(TestSuite.class.getName()).resolve("report.json")).doesNotExist();
  }

  private static final class TestSuite {}
}
