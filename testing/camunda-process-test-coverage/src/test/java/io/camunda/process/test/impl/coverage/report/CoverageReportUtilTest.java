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

import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageReport;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageSuiteReport;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class CoverageReportUtilTest {

  @Test
  void shouldSerializeObjectToJson() {
    // given
    final Map<String, String> testObject = new HashMap<>();
    testObject.put("key1", "value1");
    testObject.put("key2", "value2");

    // when
    final String json = CoverageReportUtil.toJson(testObject);

    // then
    assertThat(json).contains("\"key1\" : \"value1\"");
    assertThat(json).contains("\"key2\" : \"value2\"");
  }

  @Disabled(
      "The frontend resources are not generate in the CI build. Should be fixed by https://github.com/camunda/camunda/issues/48222.")
  @Test
  void shouldGenerateHtmlReportFromTemplate() {
    // given
    final CoverageReport report =
        ImmutableCoverageReport.builder()
            .addSuites(ImmutableCoverageSuiteReport.builder().id("suite").name("Suite").build())
            .build();

    // when
    final String html = CoverageReportUtil.toHtml(report);

    // then
    assertThat(html).contains("<!doctype html>");
    assertThat(html).contains("\"suites\"");
    assertThat(html).contains("\"id\" : \"suite\"");
  }
}
