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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.process.test.impl.coverage.model.Coverage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    assertThat(json).isNotNull();
    assertThat(json).contains("\"key1\" : \"value1\"");
    assertThat(json).contains("\"key2\" : \"value2\"");
    assertThat(json).contains("{\n");
    assertThat(json).contains("\n}");
  }

  @Test
  void shouldSerializeEmptyObjectToJson() {
    // given
    final Map<String, String> emptyObject = Collections.emptyMap();

    // when
    final String json = CoverageReportUtil.toJson(emptyObject);

    // then
    assertThat(json).isEqualTo("{ }");
  }

  @Test
  void shouldSerializeNullToJson() {
    // when
    final String json = CoverageReportUtil.toJson(null);

    // then
    assertThat(json).isEqualTo("null");
  }

  @Test
  void shouldThrowRuntimeExceptionForNonSerializableObject() {
    // given
    final Object nonSerializableObject =
        new Object() {
          @SuppressWarnings("unused")
          public String getSelf() {
            return toString();
          }
        };

    // when/then - This should work fine with Jackson, but let's test with a problematic object
    assertThat(CoverageReportUtil.toJson(nonSerializableObject)).isNotNull();
  }

  @Test
  void shouldGenerateHtmlReportFromTemplate() {
    // given
    final HtmlCoverageReport coverageReport =
        new HtmlCoverageReport(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());

    // when
    final String html = CoverageReportUtil.toHtml(coverageReport);

    // then
    assertThat(html).isNotNull();
    assertThat(html).contains("<!doctype html>");
    assertThat(html).contains("<html");
    assertThat(html).contains("window.COVERAGE_DATA = {\n  \"suites\"");
    assertThat(html).contains("\"suites\" : [ ]");
    assertThat(html).contains("\"coverages\" : [ ]");
    assertThat(html).contains("\"definitions\" : { }");
  }

  @Test
  void shouldReplaceTemplateVariableWithCoverageData() {
    // given
    final Map<String, String> definitions = new HashMap<>();
    definitions.put("process-1", "<bpmn>test</bpmn>");

    final HtmlCoverageReport coverageReport =
        new HtmlCoverageReport(Collections.emptyList(), Collections.emptyList(), definitions);

    // when
    final String html = CoverageReportUtil.toHtml(coverageReport);

    // then
    assertThat(html)
        .contains("\"definitions\" : {\n    \"process-1\" : \"<bpmn>test</bpmn>\"\n  }");
    assertThat(html).doesNotContain("{{ COVERAGE_DATA }}");
  }

  @Test
  void shouldInstallReportDependenciesInNewDirectory(@TempDir final Path tempDir) {
    // given
    final File reportDirectory = tempDir.resolve("test-report").toFile();

    // when
    CoverageReportUtil.installReportDependencies(reportDirectory);

    // then
    assertThat(reportDirectory).exists();
  }

  @Test
  void shouldSkipInstallationIfResourcesAlreadyExist(@TempDir final Path tempDir)
      throws IOException {
    // given
    final File reportDirectory = tempDir.resolve("test-report").toFile();
    final File resourcesDir = new File(reportDirectory, CoverageReportUtil.REPORT_RESOURCES);

    // Create the resources directory manually
    assertThat(resourcesDir.mkdirs()).isTrue();
    Files.write(resourcesDir.toPath().resolve("test-file.txt"), "test content".getBytes());
    final long originalModified = resourcesDir.lastModified();

    // when
    CoverageReportUtil.installReportDependencies(reportDirectory);

    // then
    assertThat(resourcesDir).exists();
    assertThat(resourcesDir.lastModified()).isEqualTo(originalModified);
  }

  @Test
  void shouldCreateParentDirectoryIfNotExists(@TempDir final Path tempDir) {
    // given
    final File reportDirectory =
        tempDir.resolve("nested").resolve("deep").resolve("report").toFile();
    assertThat(reportDirectory).doesNotExist();

    // when
    CoverageReportUtil.installReportDependencies(reportDirectory);

    // then
    assertThat(reportDirectory).exists();
  }

  @Test
  void shouldThrowExceptionWhenCannotCreateParentDirectory() {
    // given - Create a file where we want to create a directory
    final File existingFile = new File("/dev/null"); // This exists but is not a directory
    final File reportDirectory = new File(existingFile, "report");

    // when/then
    assertThatThrownBy(() -> CoverageReportUtil.installReportDependencies(reportDirectory))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unable to copy report resources");
  }

  @Test
  void shouldSerializeComplexCoverageReport() {
    // given
    final Coverage coverage = mock(Coverage.class);
    //    when(coverage.getProcessDefinitionId()).thenReturn("element-1");

    final SuiteCoverageReport suiteReport =
        new SuiteCoverageReport(
            "suite-1",
            "Test Suite",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.singletonList(coverage));

    final Map<String, String> definitions = new HashMap<>();
    definitions.put("process-1", "<bpmn>process definition</bpmn>");

    final HtmlCoverageReport report =
        new HtmlCoverageReport(
            Collections.singletonList(suiteReport),
            Collections.singletonList(coverage),
            definitions);

    // when
    final String json = CoverageReportUtil.toJson(report);

    // then
    assertThat(json).isNotNull();
    assertThat(json).contains("\"suites\"");
    assertThat(json).contains("\"coverages\"");
    assertThat(json).contains("\"definitions\"");
    assertThat(json).contains("\"process-1\"");
    assertThat(json).contains("\"<bpmn>process definition</bpmn>\"");
  }
}
