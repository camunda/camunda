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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.api.coverage.model.CoverageSuiteReport;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageReport;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageRunReport;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageSuiteReport;
import io.camunda.process.test.api.coverage.model.ImmutableDecisionCoverage;
import io.camunda.process.test.api.coverage.model.ImmutableDecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableProcessCoverage;
import io.camunda.process.test.api.coverage.model.ProcessCoverage;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.impl.coverage.core.CoverageReportCollector;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/**
 * Tests for {@link CoverageReporter}.
 *
 * <p>This test class is in the same package as {@link CoverageReportUtil} so that the
 * package-private {@code installReportDependencies} method can be verified via {@code
 * Mockito.mockStatic} when needed.
 *
 * <p>Most tests bypass {@code installReportDependencies} by pre-creating the {@code
 * coverage/static} subdirectory under the {@code @TempDir}, which causes the method to return
 * immediately (it only copies resources when the directory does not yet exist). The test classpath
 * provides {@code coverage/index.html} via {@code src/test/resources} so that {@link
 * CoverageReportUtil#toHtml} can read the template without a full frontend build.
 *
 * <p>{@link CoverageReportCollector} is mocked (Mockito 5 supports mocking {@code final} classes
 * out of the box) so that tests do not require a live Camunda engine.
 */
class CoverageReporterTest {

  private static final String PROCESS_ID = "process-a";

  /** The real, fully deployed model: 4 flow nodes and 3 sequence flows. */
  private static final ProcessModel REAL_MODEL =
      ProcessModelFixtures.modelOf(
          PROCESS_ID,
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent("startA")
              .sequenceFlowId("flowA1")
              .serviceTask("taskA1")
              .sequenceFlowId("flowA2")
              .serviceTask("taskA2")
              .sequenceFlowId("flowA3")
              .endEvent("endA")
              .done());

  /** The stub that {@code MOCK_CHILD_PROCESS} deploys under the mocked process id. */
  private static final ProcessModel MOCK_STUB_MODEL =
      ProcessModelFixtures.modelOf(
          PROCESS_ID,
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent("child-start")
              .sequenceFlowId("child-flow")
              .endEvent("child-end")
              .done());

  /** The real suite covers 5 of the 7 elements: the process instance is still running. */
  private static final ProcessCoverage REAL_COVERAGE =
      ImmutableProcessCoverage.builder()
          .processDefinitionId(PROCESS_ID)
          .addCompletedElements("startA", "taskA1", "taskA2")
          .addTakenSequenceFlows("flowA1", "flowA2")
          .coverage(5.0 / 7.0)
          .build();

  private static final ProcessCoverage MOCK_STUB_COVERAGE =
      ImmutableProcessCoverage.builder()
          .processDefinitionId(PROCESS_ID)
          .addCompletedElements("child-start", "child-end")
          .addTakenSequenceFlows("child-flow")
          .coverage(1.0)
          .build();

  private static final String DECISION_ID = "decision-a";

  /** The full decision table, as one suite deploys it. */
  private static final DecisionModel FULL_TABLE =
      DecisionModelFixtures.modelOf(DECISION_ID, "full-1", "full-2", "full-3", "full-4", "full-5");

  /** A smaller table that another suite deploys under the same decision definition id. */
  private static final DecisionModel SMALL_TABLE =
      DecisionModelFixtures.modelOf(DECISION_ID, "small-1", "small-2");

  @TempDir File tempDir;

  /**
   * Builds a mock {@link CoverageReportCollector} whose {@code getSuite()}, {@code getModels()},
   * and {@code getDecisionModels()} return pre-built data.
   */
  private CoverageReportCollector buildCollector(
      final Class<?> testClass,
      final String processDefinitionId,
      final double processCoverage,
      final String decisionDefinitionId,
      final double decisionCoverage) {

    final CoverageSuiteReport suite =
        ImmutableCoverageSuiteReport.builder()
            .id(testClass.getName())
            .name(testClass.getSimpleName())
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run-1")
                    .addProcessCoverages(
                        ImmutableProcessCoverage.builder()
                            .processDefinitionId(processDefinitionId)
                            .addCompletedElements("task")
                            .addTakenSequenceFlows("flow-a")
                            .addTakenSequenceFlows("flow-b")
                            .coverage(processCoverage)
                            .build())
                    .addDecisionCoverages(
                        ImmutableDecisionCoverage.builder()
                            .decisionDefinitionId(decisionDefinitionId)
                            .addMatchedRuleIds("rule-1")
                            .addMatchedRuleIndices(1)
                            .coverage(decisionCoverage)
                            .build())
                    .build())
            .build();

    final List<ProcessModel> processModels =
        Collections.singletonList(
            ProcessModelFixtures.modelOf(
                processDefinitionId,
                Bpmn.createExecutableProcess(processDefinitionId)
                    .startEvent("start")
                    .sequenceFlowId("flow-a")
                    .serviceTask("task")
                    .sequenceFlowId("flow-b")
                    .endEvent("end")
                    .done()));

    final List<DecisionModel> decisionModels =
        Collections.singletonList(
            ImmutableDecisionModel.builder()
                .decisionDefinitionId(decisionDefinitionId)
                .totalRuleCount(2)
                .version("1")
                .xml("<dmn/>")
                .build());

    final CoverageReportCollector collector = mock(CoverageReportCollector.class);
    when(collector.getSuite()).thenReturn(suite);
    when(collector.getModels()).thenReturn(processModels);
    when(collector.getDecisionModels()).thenReturn(decisionModels);
    return collector;
  }

  // ── printCoverage tests ───────────────────────────────────────────────────────

  @Test
  void shouldPrintProcessCoveragePercentageToStream() {
    // given
    final List<String> captured = new ArrayList<>();
    final CoverageReporter reporter =
        new CoverageReporter(tempDir.getAbsolutePath(), captured::add);
    final CoverageReportCollector collector =
        buildCollector(PrintProcessTest.class, "my-process", 0.75, "my-decision", 0.5);

    // when
    reporter.printCoverage(collector);

    // then: exactly one message was printed
    assertThat(captured).hasSize(1);
    final String message = captured.get(0);

    // the message includes the suite identifier (class name), process ID, and coverage percentage
    assertThat(message).contains(PrintProcessTest.class.getName());
    assertThat(message).contains("my-process");
    assertThat(message).contains("60%");
  }

  @Test
  void shouldPrintDecisionCoveragePercentageToStream() {
    // given
    final List<String> captured = new ArrayList<>();
    final CoverageReporter reporter =
        new CoverageReporter(tempDir.getAbsolutePath(), captured::add);
    final CoverageReportCollector collector =
        buildCollector(PrintDecisionTest.class, "p-decision", 1.0, "my-decision", 0.5);

    // when
    reporter.printCoverage(collector);

    // then
    assertThat(captured).hasSize(1);
    final String message = captured.get(0);
    assertThat(message).contains("my-decision");
    assertThat(message).contains("50%");
  }

  @Test
  void shouldPrintHtmlReportFilePathToStream() {
    // given
    final List<String> captured = new ArrayList<>();
    final CoverageReporter reporter =
        new CoverageReporter(tempDir.getAbsolutePath(), captured::add);
    final CoverageReportCollector collector =
        buildCollector(PrintPathTest.class, "some-process", 1.0, "some-decision", 1.0);

    // when
    reporter.printCoverage(collector);

    // then: the message references the HTML report path
    assertThat(captured).hasSize(1);
    assertThat(captured.get(0)).contains("report.html");
  }

  // ── writeJsonReport tests ────────────────────────────────────────────────────

  @Test
  void shouldWriteJsonReportFileWithCoverageData() throws Exception {
    // given: pre-create static dir so installReportDependencies is a no-op
    new File(tempDir, "coverage/static").mkdirs();

    final CoverageReporter reporter = new CoverageReporter(tempDir.getAbsolutePath(), s -> {});
    final CoverageReportCollector collector =
        buildCollector(WriteJsonTest.class, "my-process", 0.8, "my-decision", 0.5);

    // when
    reporter.createAggregatedReport(Collections.singletonList(collector));

    // then: report.json exists and contains valid JSON with coverage data
    final File reportJson = new File(tempDir, "report.json");
    assertThat(reportJson).exists();

    final String json = new String(Files.readAllBytes(reportJson.toPath()));
    new ObjectMapper().readTree(json); // throws if not valid JSON

    assertThat(json).contains("\"suites\"");
    assertThat(json).contains("my-process");
    assertThat(json).contains("my-decision");
  }

  @Test
  void shouldWriteJsonReportContainingProcessAndDecisionModels() throws Exception {
    // given: pre-create static dir so installReportDependencies is a no-op
    new File(tempDir, "coverage/static").mkdirs();

    final CoverageReporter reporter = new CoverageReporter(tempDir.getAbsolutePath(), s -> {});

    final CoverageSuiteReport suite =
        ImmutableCoverageSuiteReport.builder()
            .id(WriteJsonModelsTest.class.getName())
            .name("WriteJsonModelsTest")
            .addRuns(ImmutableCoverageRunReport.builder().name("run").build())
            .build();

    final Collection<ProcessModel> processModels =
        Collections.singletonList(
            ProcessModelFixtures.modelOf(
                "proc-model",
                Bpmn.createExecutableProcess("proc-model")
                    .startEvent("start")
                    .endEvent("end")
                    .done()));

    final Collection<DecisionModel> decisionModels =
        Collections.singletonList(
            ImmutableDecisionModel.builder()
                .decisionDefinitionId("dec-model")
                .totalRuleCount(2)
                .version("1")
                .xml("<dmn/>")
                .build());

    final CoverageReportCollector collector = mock(CoverageReportCollector.class);
    when(collector.getSuite()).thenReturn(suite);
    when(collector.getModels()).thenReturn(processModels);
    when(collector.getDecisionModels()).thenReturn(decisionModels);

    // when
    reporter.createAggregatedReport(Collections.singletonList(collector));

    // then
    final File reportJson = new File(tempDir, "report.json");
    assertThat(reportJson).exists();
    final String json = new String(Files.readAllBytes(reportJson.toPath()));
    assertThat(json).contains("\"processModels\"");
    assertThat(json).contains("proc-model");
    assertThat(json).contains("\"decisionModels\"");
    assertThat(json).contains("dec-model");
  }

  // ── writeHtmlReport tests ────────────────────────────────────────────────────

  @Test
  void shouldWriteHtmlReportFileContainingCoverageData() throws Exception {
    // given: pre-create static dir so installReportDependencies is a no-op
    new File(tempDir, "coverage/static").mkdirs();

    final CoverageReporter reporter = new CoverageReporter(tempDir.getAbsolutePath(), s -> {});
    final CoverageReportCollector collector =
        buildCollector(WriteHtmlTest.class, "html-process", 0.6, "html-decision", 0.4);

    // when
    reporter.createAggregatedReport(Collections.singletonList(collector));

    // then: report.html exists and contains the serialised coverage data
    final File reportHtml = new File(tempDir, "report.html");
    assertThat(reportHtml).exists();

    final String html = new String(Files.readAllBytes(reportHtml.toPath()));
    assertThat(html).contains("<!DOCTYPE html>");
    assertThat(html).contains("\"suites\"");
    assertThat(html).contains("html-process");
    assertThat(html).contains("html-decision");
  }

  @Test
  void shouldInvokeInstallReportDependenciesWhenWritingHtmlReport() throws Exception {
    // given
    try (final MockedStatic<CoverageReportUtil> mockedUtil =
        mockStatic(CoverageReportUtil.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
      mockedUtil
          .when(() -> CoverageReportUtil.installReportDependencies(any()))
          .thenAnswer(invocation -> null);

      final CoverageReporter reporter = new CoverageReporter(tempDir.getAbsolutePath(), s -> {});
      final CoverageReportCollector collector =
          buildCollector(InstallDepsTest.class, "process", 1.0, "decision", 1.0);

      // when
      reporter.createAggregatedReport(Collections.singletonList(collector));

      // then: installReportDependencies was called once with the reporter's resource directory
      mockedUtil.verify(() -> CoverageReportUtil.installReportDependencies(any()));
    }
  }

  // ── createSuiteCoverageReport test ───────────────────────────────────────────

  @Test
  void shouldReturnSuiteCoverageReportFromCollector() {
    // given
    final CoverageReporter reporter = new CoverageReporter(tempDir.getAbsolutePath(), s -> {});
    final CoverageReportCollector collector =
        buildCollector(SuiteReportTest.class, "suite-process", 1.0, "suite-decision", 0.5);

    // when
    final CoverageReport report = reporter.createSuiteCoverageReport(collector);

    // then
    assertThat(report.getSuites()).hasSize(1);
    assertThat(report.getSuites().get(0).getId()).isEqualTo(SuiteReportTest.class.getName());
  }

  // ── createAggregatedReport test ──────────────────────────────────────────────

  @Test
  void shouldReturnAggregatedReportFromMultipleCollectors() throws Exception {
    // given: pre-create static dir so installReportDependencies is a no-op
    new File(tempDir, "coverage/static").mkdirs();

    final CoverageReporter reporter = new CoverageReporter(tempDir.getAbsolutePath(), s -> {});
    final CoverageReportCollector collectorA =
        buildCollector(AggregatedCollectorTestA.class, "process-a", 1.0, "decision-a", 1.0);
    final CoverageReportCollector collectorB =
        buildCollector(AggregatedCollectorTestB.class, "process-b", 0.5, "decision-b", 0.5);

    // when
    final CoverageReport report =
        reporter.createAggregatedReport(Arrays.asList(collectorA, collectorB));

    // then: the aggregated report includes both suites
    assertThat(report.getSuites()).hasSize(2);
  }

  // ── process mocked in another suite (SUPPORT-34357) ──────────────────────────

  /**
   * A process that another suite mocks via {@code MOCK_CHILD_PROCESS} is deployed twice under the
   * same process definition id: once as the real model, and once as the tiny stub the mock deploys.
   * The aggregated report must describe the process by its real model, not by the stub.
   */
  @Test
  void shouldReportRealModelWhenProcessIsAlsoMockedInAnotherSuite() {
    // given: pre-create static dir so installReportDependencies is a no-op
    new File(tempDir, "coverage/static").mkdirs();
    final CoverageReporter reporter = new CoverageReporter(tempDir.getAbsolutePath(), s -> {});

    // and: the suite that mocks the process is reported before the suite that tests it
    final CoverageReportCollector mockingSuite =
        buildCollectorForProcess(MockingSuiteTest.class, MOCK_STUB_MODEL, MOCK_STUB_COVERAGE);
    final CoverageReportCollector realSuite =
        buildCollectorForProcess(RealProcessSuiteTest.class, REAL_MODEL, REAL_COVERAGE);

    // when
    final CoverageReport report =
        reporter.createAggregatedReport(Arrays.asList(mockingSuite, realSuite));

    // then
    assertThat(report.getProcessModels())
        .filteredOn(model -> model.getProcessDefinitionId().equals(PROCESS_ID))
        .singleElement()
        .extracting(ProcessModel::getXml)
        .isEqualTo(REAL_MODEL.getXml());
  }

  /**
   * The elements of the mock stub do not exist in the real model, so they must not count towards
   * the real process's coverage - otherwise the aggregated coverage exceeds 100%.
   */
  @Test
  void shouldIgnoreMockStubElementsWhenProcessIsAlsoMockedInAnotherSuite() {
    // given: pre-create static dir so installReportDependencies is a no-op
    new File(tempDir, "coverage/static").mkdirs();
    final CoverageReporter reporter = new CoverageReporter(tempDir.getAbsolutePath(), s -> {});

    // and: the suite that mocks the process is reported before the suite that tests it
    final CoverageReportCollector mockingSuite =
        buildCollectorForProcess(MockingSuiteTest.class, MOCK_STUB_MODEL, MOCK_STUB_COVERAGE);
    final CoverageReportCollector realSuite =
        buildCollectorForProcess(RealProcessSuiteTest.class, REAL_MODEL, REAL_COVERAGE);

    // when
    final CoverageReport report =
        reporter.createAggregatedReport(Arrays.asList(mockingSuite, realSuite));

    // then: only the 5 of 7 elements covered by the real suite count
    assertThat(report.getProcessCoverages())
        .filteredOn(coverage -> coverage.getProcessDefinitionId().equals(PROCESS_ID))
        .singleElement()
        .extracting(ProcessCoverage::getCoverage)
        .isEqualTo(5.0 / 7.0);
  }

  /** Builds a mock collector reporting a single process coverage against a single model. */
  private CoverageReportCollector buildCollectorForProcess(
      final Class<?> testClass, final ProcessModel model, final ProcessCoverage coverage) {

    final CoverageSuiteReport suite =
        ImmutableCoverageSuiteReport.builder()
            .id(testClass.getName())
            .name(testClass.getSimpleName())
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run-1")
                    .addProcessCoverages(coverage)
                    .build())
            .build();

    final CoverageReportCollector collector = mock(CoverageReportCollector.class);
    when(collector.getSuite()).thenReturn(suite);
    when(collector.getModels()).thenReturn(Collections.singletonList(model));
    when(collector.getDecisionModels()).thenReturn(Collections.emptyList());
    return collector;
  }

  // ── decision deployed as different tables under one id ───────────────────────

  /**
   * Suites can deploy different tables under the same decision definition id, for example when
   * their fixtures differ in the rules they define. The aggregated report must describe the
   * decision by the table that has the most rules, otherwise the rules matched by the other suites
   * are counted against a table that does not have them.
   */
  @Test
  void shouldReportMostCompleteTableWhenSuitesDeployDifferentDecisionTables() {
    // given: pre-create static dir so installReportDependencies is a no-op
    new File(tempDir, "coverage/static").mkdirs();
    final CoverageReporter reporter = new CoverageReporter(tempDir.getAbsolutePath(), s -> {});

    // and: the suite with the smaller table is reported first
    final CoverageReportCollector smallTableSuite =
        buildCollectorForDecision(SmallTableSuiteTest.class, SMALL_TABLE, "small-1", "small-2");
    final CoverageReportCollector fullTableSuite =
        buildCollectorForDecision(FullTableSuiteTest.class, FULL_TABLE, "full-1");

    // when
    final CoverageReport report =
        reporter.createAggregatedReport(Arrays.asList(smallTableSuite, fullTableSuite));

    // then
    assertThat(report.getDecisionModels())
        .filteredOn(model -> model.getDecisionDefinitionId().equals(DECISION_ID))
        .singleElement()
        .extracting(DecisionModel::getTotalRuleCount)
        .isEqualTo(5);
  }

  /** Builds a mock collector reporting a single decision coverage against a single table. */
  private CoverageReportCollector buildCollectorForDecision(
      final Class<?> testClass, final DecisionModel model, final String... matchedRuleIds) {

    final CoverageSuiteReport suite =
        ImmutableCoverageSuiteReport.builder()
            .id(testClass.getName())
            .name(testClass.getSimpleName())
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run-1")
                    .addDecisionCoverages(
                        ImmutableDecisionCoverage.builder()
                            .decisionDefinitionId(model.getDecisionDefinitionId())
                            .addMatchedRuleIds(matchedRuleIds)
                            .coverage((double) matchedRuleIds.length / model.getTotalRuleCount())
                            .build())
                    .build())
            .build();

    final CoverageReportCollector collector = mock(CoverageReportCollector.class);
    when(collector.getSuite()).thenReturn(suite);
    when(collector.getModels()).thenReturn(Collections.emptyList());
    when(collector.getDecisionModels()).thenReturn(Collections.singletonList(model));
    return collector;
  }

  // ── JSON serialisation test (migrated from CoverageReportUtilTest) ───────────

  @Test
  void shouldSerialiseReportToValidJson() {
    // given
    final CoverageReport report =
        ImmutableCoverageReport.builder()
            .addSuites(ImmutableCoverageSuiteReport.builder().id("s1").name("Suite1").build())
            .build();

    // when
    final String json = CoverageReportUtil.toJson(report);

    // then
    assertThat(json).contains("\"suites\"");
    assertThat(json).contains("\"id\" : \"s1\"");
    assertThat(json).contains("\"name\" : \"Suite1\"");
  }
}

// Test fixture marker classes used as suite identifiers (unique per test method)
final class PrintProcessTest {}

final class PrintDecisionTest {}

final class PrintPathTest {}

final class WriteJsonTest {}

final class WriteJsonModelsTest {}

final class WriteHtmlTest {}

final class InstallDepsTest {}

final class SuiteReportTest {}

final class AggregatedCollectorTestA {}

final class AggregatedCollectorTestB {}

final class MockingSuiteTest {}

final class RealProcessSuiteTest {}

final class SmallTableSuiteTest {}

final class FullTableSuiteTest {}
