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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.process.test.api.coverage.model.ImmutableSuite;
import io.camunda.process.test.impl.coverage.core.CoverageReportCollector;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverageReporterTest {

  @Test
  void shouldPrintDeepLinkToSuiteInCoverageReport(@TempDir final Path tempDir) {
    // given
    final String suiteId = "io.camunda.InvoiceApprovalTest";
    final CoverageReportCollector coverageReportCollector = mock(CoverageReportCollector.class);
    when(coverageReportCollector.getSuite())
        .thenReturn(ImmutableSuite.builder().id(suiteId).name("InvoiceApprovalTest").build());
    when(coverageReportCollector.getModels()).thenReturn(Collections.emptyList());
    when(coverageReportCollector.getDecisionModels()).thenReturn(Collections.emptyList());

    final AtomicReference<String> printed = new AtomicReference<>();
    final CoverageReporter coverageReporter =
        new CoverageReporter(tempDir.toString(), printed::set);

    // when
    coverageReporter.printCoverage(coverageReportCollector);

    // then
    assertThat(printed.get())
        .contains(
            "Coverage report: file://"
                + tempDir.toAbsolutePath()
                + "/report.html#/suite/"
                + suiteId);
  }
}
