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
package io.camunda.process.test.api.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.impl.coverage.CoverageCollector;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageDecisionInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessDefinitionData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageTestData;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoverageReportCollectorBuilderTest {

  @TempDir File tempDir;

  @Mock private ProcessInstance processInstance;

  @Mock private DecisionInstance decisionInstance;

  @Test
  void shouldApplyExclusionConfigurationToCoverageResult() {
    // given
    when(processInstance.getProcessDefinitionId()).thenReturn("excluded-process");

    when(decisionInstance.getDecisionDefinitionId()).thenReturn("excluded-decision");
    when(decisionInstance.getDecisionDefinitionType())
        .thenReturn(DecisionDefinitionType.DECISION_TABLE);

    final CoverageTestData testResults =
        ImmutableCoverageTestData.builder()
            .addProcessInstanceData(
                ImmutableCoverageProcessInstanceData.builder()
                    .processInstance(processInstance)
                    .build())
            .addDecisionInstanceData(
                ImmutableCoverageDecisionInstanceData.builder()
                    .decisionInstance(decisionInstance)
                    .build())
            .build();

    final CoverageCollector coverageCollector =
        CoverageCollector.newBuilder()
            .excludeProcessDefinitionIds(java.util.Collections.singletonList("excluded-process"))
            .excludeDecisionDefinitionIds(java.util.Collections.singletonList("excluded-decision"))
            .build();

    // when
    final CoverageReport report =
        coverageCollector.collectTestRunCoverage(ExclusionTest.class, "run-1", null, testResults);

    // then
    assertThat(report.getSuites())
        .singleElement()
        .satisfies(suite -> assertThat(suite.getRuns()).hasSize(1));
    assertThat(report.getProcessCoverages()).isEmpty();
    assertThat(report.getDecisionCoverages()).isEmpty();
  }

  @Test
  void shouldIncludeGivenRunAndCollectedDataInSuiteReport() {
    // given
    final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();
    final String processDefinitionId = "process-a";
    final CoverageTestData testData = createProcessCoverageTestData(processDefinitionId, "taskA");

    // when
    final CoverageReport report =
        coverageCollector.collectTestRunCoverage(GivenRunTest.class, "run-1", null, testData);

    // then
    assertThat(report.getSuites())
        .singleElement()
        .satisfies(
            suite -> {
              assertThat(suite.getId()).isEqualTo(GivenRunTest.class.getName());
              assertThat(suite.getName()).isEqualTo("GivenRunTest");
              assertThat(suite.getRuns())
                  .singleElement()
                  .satisfies(
                      run -> {
                        assertThat(run.getName()).isEqualTo("run-1");
                        assertThat(run.getProcessCoverages())
                            .singleElement()
                            .satisfies(
                                coverage -> {
                                  assertThat(coverage.getProcessDefinitionId())
                                      .isEqualTo(processDefinitionId);
                                  assertThat(coverage.getCompletedElements()).contains("taskA");
                                });
                      });
            });

    assertThat(report.getProcessModels())
        .singleElement()
        .satisfies(
            model -> {
              assertThat(model.getProcessDefinitionId()).isEqualTo(processDefinitionId);
              assertThat(model.getXml()).contains("id=\"" + processDefinitionId + "\"");
            });
  }

  @Test
  void shouldUseEnclosingSuiteNameAndKeepNestedRunNameForNestedTests() {
    // given
    final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();
    final CoverageTestData testData = createProcessCoverageTestData("process-a", "taskA");

    // when
    final CoverageReport report =
        coverageCollector.collectTestRunCoverage(
            NestedSuiteFixture.NestedSuiteTest.class, "NestedSuiteTest#run-1", null, testData);

    // then
    assertThat(report.getSuites())
        .singleElement()
        .satisfies(
            suite -> {
              assertThat(suite.getName()).isEqualTo("NestedSuiteFixture");
              assertThat(suite.getRuns())
                  .singleElement()
                  .satisfies(run -> assertThat(run.getName()).isEqualTo("NestedSuiteTest#run-1"));
            });
  }

  @Test
  void shouldIncludeCollectedDataInAggregatedReport() {
    // given
    final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();
    coverageCollector.collectTestRunCoverage(
        AggregatedReportTest.class,
        "run-1",
        null,
        createProcessCoverageTestData("process-a", "taskA"));
    final CoverageReport report =
        coverageCollector.collectTestRunCoverage(
            AggregatedReportTest.class,
            "run-2",
            null,
            createProcessCoverageTestData("process-b", "taskB"));

    // then
    assertThat(report.getSuites())
        .singleElement()
        .satisfies(
            suite -> {
              assertThat(suite.getRuns())
                  .extracting(run -> run.getName())
                  .containsExactly("run-1", "run-2");
            });
    assertThat(report.getProcessCoverages()).hasSize(2);
    assertThat(report.getProcessModels())
        .extracting(model -> model.getProcessDefinitionId())
        .containsExactlyInAnyOrder("process-a", "process-b");
  }

  @Test
  void shouldCombineNestedSuitesOfSameEnclosingClassInSingleReport() {
    // given
    final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();

    // when
    coverageCollector.collectTestRunCoverage(
        NestedCollectorFixture.SecondNestedSuiteTest.class,
        "SecondNestedSuiteTest#run-2",
        null,
        createProcessCoverageTestData("process-b", "taskB"));
    final CoverageReport report =
        coverageCollector.collectTestRunCoverage(
            NestedCollectorFixture.FirstNestedSuiteTest.class,
            "FirstNestedSuiteTest#run-1",
            null,
            createProcessCoverageTestData("process-a", "taskA"));

    // then
    assertThat(report.getSuites())
        .singleElement()
        .satisfies(
            suite -> {
              assertThat(suite.getName()).isEqualTo("NestedCollectorFixture");
              assertThat(suite.getRuns())
                  .extracting(run -> run.getName())
                  .containsExactlyInAnyOrder(
                      "SecondNestedSuiteTest#run-2", "FirstNestedSuiteTest#run-1");
            });
  }

  @Test
  void shouldGenerateReportAndPrintCoverageSummaryToStream() throws Exception {
    // given: pre-create the static resources directory so installReportDependencies is a no-op
    new File(tempDir, "coverage/static").mkdirs();

    final List<String> captured = new ArrayList<>();
    final CoverageCollector coverageCollector =
        CoverageCollector.newBuilder()
            .reportDirectory(tempDir.getAbsolutePath())
            .printStream(captured::add)
            .build();

    coverageCollector.collectTestRunCoverage(
        GenerateReportFixture.class,
        "shouldGenerateReport",
        null,
        createProcessCoverageTestData("generate-report-process", "genTask"));

    // when
    final CoverageReport report = coverageCollector.generateReport(GenerateReportFixture.class);

    // then: print stream contains the suite name and process coverage percentage
    assertThat(captured).hasSize(1);
    final String message = captured.get(0);
    assertThat(message).contains(GenerateReportFixture.class.getName());
    assertThat(message).contains("generate-report-process");

    // and the returned aggregated report includes the fixture suite
    assertThat(report.getSuites())
        .anySatisfy(
            suite -> assertThat(suite.getId()).isEqualTo(GenerateReportFixture.class.getName()));
  }

  @Test
  void shouldGenerateReportAndWriteJsonFile() throws Exception {
    // given: pre-create the static resources directory so installReportDependencies is a no-op
    new File(tempDir, "coverage/static").mkdirs();

    final CoverageCollector coverageCollector =
        CoverageCollector.newBuilder().reportDirectory(tempDir.getAbsolutePath()).build();

    coverageCollector.collectTestRunCoverage(
        GenerateReportJsonFixture.class,
        "shouldWriteJson",
        null,
        createProcessCoverageTestData("json-fixture-process", "jsonTask"));

    // when
    coverageCollector.generateReport(GenerateReportJsonFixture.class);

    // then: a report.json file is written to the temp directory
    final File reportJson = new File(tempDir, "report.json");
    assertThat(reportJson).exists();

    final String json = new java.lang.String(java.nio.file.Files.readAllBytes(reportJson.toPath()));
    assertThat(json).contains("\"suites\"");
  }

  private CoverageTestData createProcessCoverageTestData(
      final String processDefinitionId, final String completedElementId) {
    final ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessDefinitionId()).thenReturn(processDefinitionId);

    final ElementInstance elementInstance = mock(ElementInstance.class);
    when(elementInstance.getElementId()).thenReturn(completedElementId);
    when(elementInstance.getType()).thenReturn(ElementInstanceType.SERVICE_TASK);
    when(elementInstance.getState()).thenReturn(ElementInstanceState.COMPLETED);

    final ProcessInstanceSequenceFlow sequenceFlow = mock(ProcessInstanceSequenceFlow.class);
    when(sequenceFlow.getElementId()).thenReturn("flow-" + completedElementId);

    final ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getProcessDefinitionId()).thenReturn(processDefinitionId);
    when(processDefinition.getVersion()).thenReturn(1);

    return ImmutableCoverageTestData.builder()
        .addProcessInstanceData(
            ImmutableCoverageProcessInstanceData.builder()
                .processInstance(processInstance)
                .addAllElementInstances(java.util.Collections.singletonList(elementInstance))
                .addAllSequenceFlows(java.util.Collections.singletonList(sequenceFlow))
                .build())
        .addProcessDefinitionData(
            ImmutableCoverageProcessDefinitionData.builder()
                .processDefinition(processDefinition)
                .xml(
                    processXml(
                        processDefinitionId, completedElementId, "flow-" + completedElementId))
                .build())
        .build();
  }

  private String processXml(
      final String processDefinitionId, final String completedElementId, final String flowId) {
    return Bpmn.convertToString(
        Bpmn.createExecutableProcess(processDefinitionId)
            .startEvent("start")
            .serviceTask(completedElementId)
            .endEvent("end")
            .moveToNode("start")
            .sequenceFlowId("flow-start")
            .connectTo(completedElementId)
            .moveToNode(completedElementId)
            .sequenceFlowId(flowId)
            .connectTo("end")
            .done());
  }
}

final class GivenRunTest {}

final class AggregatedReportTest {}

final class ExclusionTest {}

final class NestedSuiteFixture {
  private NestedSuiteFixture() {}

  static final class NestedSuiteTest {}
}

final class NestedCollectorFixture {
  private NestedCollectorFixture() {}

  static final class FirstNestedSuiteTest {}

  static final class SecondNestedSuiteTest {}
}

final class GenerateReportFixture {}

final class GenerateReportJsonFixture {}
