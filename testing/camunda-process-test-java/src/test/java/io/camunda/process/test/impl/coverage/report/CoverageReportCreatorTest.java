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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.camunda.process.test.impl.coverage.core.CoverageCreator;
import io.camunda.process.test.impl.coverage.model.Coverage;
import io.camunda.process.test.impl.coverage.model.Model;
import io.camunda.process.test.impl.coverage.model.Run;
import io.camunda.process.test.impl.coverage.model.Suite;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoverageReportCreatorTest {

  @Test
  void shouldCreateSuiteCoverageReport() {
    // given
    final Suite suite = mock(Suite.class);
    final Model model = mock(Model.class);
    final Run run = mock(Run.class);
    final Coverage coverage = mock(Coverage.class);

    when(suite.getId()).thenReturn("suite-1");
    when(suite.getName()).thenReturn("Test Suite 1");
    when(suite.getRuns()).thenReturn(Collections.singletonList(run));
    when(run.getCoverages()).thenReturn(Collections.singletonList(coverage));

    try (final MockedStatic<CoverageCreator> coverageCreatorMock =
        mockStatic(CoverageCreator.class)) {
      coverageCreatorMock
          .when(() -> CoverageCreator.aggregateCoverages(anyCollection(), anyCollection()))
          .thenReturn(Collections.singletonList(coverage));

      // when
      final SuiteCoverageReport report =
          CoverageReportCreator.createSuiteCoverageReport(suite, Collections.singletonList(model));

      // then
      assertThat(report).isNotNull();
      assertThat(report.getId()).isEqualTo("suite-1");
      assertThat(report.getName()).isEqualTo("Test Suite 1");
      assertThat(report.getRuns()).hasSize(1);
      assertThat(report.getModels()).hasSize(1);
      assertThat(report.getCoverages()).hasSize(1);
    }
  }

  @Test
  void shouldCreateAggregatedCoverageReport() {
    // given
    final Suite suite1 = mock(Suite.class);
    final Suite suite2 = mock(Suite.class);
    final Model model = mock(Model.class);
    final Run run1 = mock(Run.class);
    final Run run2 = mock(Run.class);
    final Coverage coverage1 = mock(Coverage.class);
    final Coverage coverage2 = mock(Coverage.class);

    when(suite1.getId()).thenReturn("suite-1");
    when(suite1.getName()).thenReturn("Test Suite 1");
    when(suite1.getRuns()).thenReturn(Collections.singletonList(run1));
    when(run1.getCoverages()).thenReturn(Collections.singletonList(coverage1));

    when(suite2.getId()).thenReturn("suite-2");
    when(suite2.getName()).thenReturn("Test Suite 2");
    when(suite2.getRuns()).thenReturn(Collections.singletonList(run2));
    when(run2.getCoverages()).thenReturn(Collections.singletonList(coverage2));

    try (final MockedStatic<CoverageCreator> coverageCreatorMock =
        mockStatic(CoverageCreator.class)) {
      coverageCreatorMock
          .when(() -> CoverageCreator.aggregateCoverages(anyCollection(), anyCollection()))
          .thenReturn(Arrays.asList(coverage1, coverage2));

      // when
      final AggregatedCoverageReport report =
          CoverageReportCreator.createAggregatedCoverageReport(
              Arrays.asList(suite1, suite2), Collections.singletonList(model));

      // then
      assertThat(report).isNotNull();
      assertThat(report.getSuites()).hasSize(2);
      assertThat(report.getModels()).hasSize(1);
      assertThat(report.getCoverages()).hasSize(2);
    }
  }

  @Test
  void shouldCreateHtmlCoverageReport() {
    // given
    final Suite suite1 = mock(Suite.class);
    final Suite suite2 = mock(Suite.class);
    final Model model1 = mock(Model.class);
    final Model model2 = mock(Model.class);
    final Run run1 = mock(Run.class);
    final Run run2 = mock(Run.class);
    final Coverage coverage1 = mock(Coverage.class);
    final Coverage coverage2 = mock(Coverage.class);

    when(suite1.getId()).thenReturn("suite-1");
    when(suite1.getName()).thenReturn("Test Suite 1");
    when(suite1.getRuns()).thenReturn(Collections.singletonList(run1));
    when(run1.getCoverages()).thenReturn(Collections.singletonList(coverage1));

    when(suite2.getId()).thenReturn("suite-2");
    when(suite2.getName()).thenReturn("Test Suite 2");
    when(suite2.getRuns()).thenReturn(Collections.singletonList(run2));
    when(run2.getCoverages()).thenReturn(Collections.singletonList(coverage2));

    when(model1.getProcessDefinitionId()).thenReturn("process-1");
    when(model1.xml()).thenReturn("<bpmn>process1</bpmn>");
    when(model2.getProcessDefinitionId()).thenReturn("process-2");
    when(model2.xml()).thenReturn("<bpmn>process2</bpmn>");

    try (final MockedStatic<CoverageCreator> coverageCreatorMock =
        mockStatic(CoverageCreator.class)) {
      coverageCreatorMock
          .when(() -> CoverageCreator.aggregateCoverages(anyCollection(), anyCollection()))
          .thenReturn(Arrays.asList(coverage1, coverage2));

      // when
      final HtmlCoverageReport report =
          CoverageReportCreator.createHtmlCoverageReport(
              Arrays.asList(suite1, suite2), Arrays.asList(model1, model2));

      // then
      assertThat(report).isNotNull();
      assertThat(report.getSuites()).hasSize(2);
      assertThat(report.getCoverages()).hasSize(2);
      assertThat(report.getDefinitions()).hasSize(2);
      assertThat(report.getDefinitions()).containsKeys("process-1", "process-2");
      assertThat(report.getDefinitions().get("process-1")).isEqualTo("<bpmn>process1</bpmn>");
      assertThat(report.getDefinitions().get("process-2")).isEqualTo("<bpmn>process2</bpmn>");
    }
  }

  @Test
  void shouldCreateHtmlCoverageReportWithEmptyCollections() {
    // given
    final List<Suite> suites = Collections.emptyList();
    final List<Model> models = Collections.emptyList();

    try (final MockedStatic<CoverageCreator> coverageCreatorMock =
        mockStatic(CoverageCreator.class)) {
      coverageCreatorMock
          .when(() -> CoverageCreator.aggregateCoverages(anyCollection(), anyCollection()))
          .thenReturn(Collections.emptyList());

      // when
      final HtmlCoverageReport report =
          CoverageReportCreator.createHtmlCoverageReport(suites, models);

      // then
      assertThat(report).isNotNull();
      assertThat(report.getSuites()).isEmpty();
      assertThat(report.getCoverages()).isEmpty();
      assertThat(report.getDefinitions()).isEmpty();
    }
  }

  @Test
  void shouldHandleDuplicateProcessDefinitionIds() {
    // given
    final Suite suite = mock(Suite.class);
    final Model model1 = mock(Model.class);
    final Model model2 = mock(Model.class);
    final Run run = mock(Run.class);
    final Coverage coverage = mock(Coverage.class);

    when(suite.getId()).thenReturn("suite-1");
    when(suite.getName()).thenReturn("Test Suite 1");
    when(suite.getRuns()).thenReturn(Collections.singletonList(run));
    when(run.getCoverages()).thenReturn(Collections.singletonList(coverage));

    when(model1.getProcessDefinitionId()).thenReturn("process-1");
    when(model1.xml()).thenReturn("<bpmn>first</bpmn>");
    when(model2.getProcessDefinitionId()).thenReturn("process-1");
    when(model2.xml()).thenReturn("<bpmn>second</bpmn>");

    try (final MockedStatic<CoverageCreator> coverageCreatorMock =
        mockStatic(CoverageCreator.class)) {
      coverageCreatorMock
          .when(() -> CoverageCreator.aggregateCoverages(anyCollection(), anyCollection()))
          .thenReturn(Collections.singletonList(coverage));

      // when
      final HtmlCoverageReport report =
          CoverageReportCreator.createHtmlCoverageReport(
              Collections.singletonList(suite), Arrays.asList(model1, model2));

      // then
      assertThat(report).isNotNull();
      assertThat(report.getDefinitions()).hasSize(1);
      assertThat(report.getDefinitions().get("process-1")).isEqualTo("<bpmn>first</bpmn>");
    }
  }

  @Test
  void shouldThrowExceptionWhenSuitesIsNull() {
    // given
    final List<Model> models = Collections.emptyList();

    // when/then
    assertThatThrownBy(() -> CoverageReportCreator.createHtmlCoverageReport(null, models))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldThrowExceptionWhenModelsIsNull() {
    // given
    final List<Suite> suites = Collections.emptyList();

    // when/then
    assertThatThrownBy(() -> CoverageReportCreator.createHtmlCoverageReport(suites, null))
        .isInstanceOf(NullPointerException.class);
  }
}
