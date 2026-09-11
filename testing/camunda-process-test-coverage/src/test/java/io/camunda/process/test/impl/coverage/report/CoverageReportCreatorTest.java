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
import io.camunda.process.test.api.coverage.model.CoverageSuiteReport;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageRunReport;
import io.camunda.process.test.api.coverage.model.ImmutableCoverageSuiteReport;
import io.camunda.process.test.api.coverage.model.ImmutableDecisionCoverage;
import io.camunda.process.test.api.coverage.model.ImmutableDecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableProcessCoverage;
import io.camunda.process.test.api.coverage.model.ProcessCoverage;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CoverageReportCreatorTest {

  @Test
  void shouldCreateAggregatedCoverageReportWithModels() {
    // given
    final ProcessModel processModel =
        ProcessModelFixtures.modelOf(
            "process",
            Bpmn.createExecutableProcess("process")
                .startEvent("start")
                .sequenceFlowId("flow")
                .serviceTask("task")
                .endEvent("end")
                .done());
    final DecisionModel decisionModel =
        ImmutableDecisionModel.builder()
            .decisionDefinitionId("decision")
            .totalRuleCount(1)
            .version("1")
            .xml("<dmn>decision</dmn>")
            .build();
    final CoverageSuiteReport suite =
        ImmutableCoverageSuiteReport.builder()
            .id("suite")
            .name("Suite")
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run")
                    .addProcessCoverages(
                        ImmutableProcessCoverage.builder()
                            .processDefinitionId("process")
                            .addCompletedElements("task")
                            .addTakenSequenceFlows("flow")
                            .coverage(1.0)
                            .build())
                    .addDecisionCoverages(
                        ImmutableDecisionCoverage.builder()
                            .decisionDefinitionId("decision")
                            .addMatchedRuleIds("rule")
                            .addMatchedRuleIndices(1)
                            .coverage(1.0)
                            .build())
                    .build())
            .build();

    // when
    final CoverageReport report =
        CoverageReportCreator.createAggregatedCoverageReport(
            Collections.singletonList(suite),
            Collections.singletonList(processModel),
            Collections.singletonList(decisionModel));

    // then
    assertThat(report.getSuites()).hasSize(1);
    assertThat(report.getProcessCoverages()).hasSize(1);
    assertThat(report.getDecisionCoverages()).hasSize(1);
    assertThat(report.getProcessModels())
        .singleElement()
        .satisfies(model -> assertThat(model.getXml()).isEqualTo(processModel.getXml()));
    assertThat(report.getDecisionModels())
        .singleElement()
        .satisfies(model -> assertThat(model.getXml()).isEqualTo("<dmn>decision</dmn>"));
  }

  @Test
  void shouldAggregateMultipleSuitesIntoSingleReport() {
    // given: two suites each covering a different process
    final ProcessModel processModelA =
        ProcessModelFixtures.modelOf(
            "process-a",
            Bpmn.createExecutableProcess("process-a")
                .startEvent("startA")
                .sequenceFlowId("flowA")
                .serviceTask("taskA")
                .endEvent("endA")
                .done());
    final ProcessModel processModelB =
        ProcessModelFixtures.modelOf(
            "process-b",
            Bpmn.createExecutableProcess("process-b")
                .startEvent("startB")
                .sequenceFlowId("flowB1")
                .serviceTask("taskB1")
                .endEvent("endB")
                .done());

    final CoverageSuiteReport suiteA =
        ImmutableCoverageSuiteReport.builder()
            .id("suite-a")
            .name("SuiteA")
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run-1")
                    .addProcessCoverages(
                        ImmutableProcessCoverage.builder()
                            .processDefinitionId("process-a")
                            .addCompletedElements("taskA")
                            .addTakenSequenceFlows("flowA")
                            .coverage(1.0)
                            .build())
                    .build())
            .build();
    final CoverageSuiteReport suiteB =
        ImmutableCoverageSuiteReport.builder()
            .id("suite-b")
            .name("SuiteB")
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run-2")
                    .addProcessCoverages(
                        ImmutableProcessCoverage.builder()
                            .processDefinitionId("process-b")
                            .addCompletedElements("taskB1")
                            .addTakenSequenceFlows("flowB1")
                            .coverage(0.5)
                            .build())
                    .build())
            .build();

    // when
    final CoverageReport report =
        CoverageReportCreator.createAggregatedCoverageReport(
            Arrays.asList(suiteA, suiteB),
            Arrays.asList(processModelA, processModelB),
            Collections.emptyList());

    // then
    assertThat(report.getSuites()).hasSize(2);
    assertThat(report.getSuites())
        .extracting(CoverageSuiteReport::getId)
        .containsExactlyInAnyOrder("suite-a", "suite-b");
    assertThat(report.getProcessCoverages()).hasSize(2);
    assertThat(report.getProcessModels()).hasSize(2);
  }

  @Test
  void shouldAggregateMultipleRunsWithinSameSuite() {
    // given: a suite with two runs both covering the same process (different elements each run)
    final ProcessModel processModel =
        ProcessModelFixtures.modelOf(
            "process",
            Bpmn.createExecutableProcess("process")
                .startEvent("element1")
                .sequenceFlowId("flow1")
                .serviceTask("element2")
                .sequenceFlowId("flow2")
                .endEvent("element3")
                .done());

    final CoverageSuiteReport suite =
        ImmutableCoverageSuiteReport.builder()
            .id("suite")
            .name("Suite")
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run-1")
                    .addProcessCoverages(
                        ImmutableProcessCoverage.builder()
                            .processDefinitionId("process")
                            .addCompletedElements("element1")
                            .addTakenSequenceFlows("flow1")
                            .coverage(0.4)
                            .build())
                    .build())
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run-2")
                    .addProcessCoverages(
                        ImmutableProcessCoverage.builder()
                            .processDefinitionId("process")
                            .addCompletedElements("element2", "element3")
                            .addTakenSequenceFlows("flow2")
                            .coverage(0.6)
                            .build())
                    .build())
            .build();

    // when
    final CoverageReport report =
        CoverageReportCreator.createAggregatedCoverageReport(
            Collections.singletonList(suite),
            Collections.singletonList(processModel),
            Collections.emptyList());

    // then: one suite with two runs
    assertThat(report.getSuites()).hasSize(1);
    assertThat(report.getSuites().get(0).getRuns()).hasSize(2);

    // and the aggregated coverage across runs covers all 5 elements → 100%
    assertThat(report.getProcessCoverages()).hasSize(1);
    assertThat(report.getProcessCoverages().get(0).getCoverage()).isEqualTo(1.0);
    assertThat(report.getProcessCoverages().get(0).getCompletedElements())
        .containsExactlyInAnyOrder("element1", "element2", "element3");
  }

  @Test
  void shouldReportEachProcessInstanceOfARunSeparately() {
    // given: a run that started the same process twice, each instance taking a different path
    final ProcessModel processModel =
        ProcessModelFixtures.modelOf(
            "process",
            Bpmn.createExecutableProcess("process")
                .startEvent("start")
                .sequenceFlowId("flow1")
                .serviceTask("task1")
                .sequenceFlowId("flow2")
                .endEvent("end")
                .done());

    final CoverageSuiteReport suite =
        ImmutableCoverageSuiteReport.builder()
            .id("suite")
            .name("Suite")
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run")
                    .addProcessCoverages(
                        ImmutableProcessCoverage.builder()
                            .processDefinitionId("process")
                            .addCompletedElements("start")
                            .addTakenSequenceFlows("flow1")
                            .coverage(0.4)
                            .build())
                    .addProcessCoverages(
                        ImmutableProcessCoverage.builder()
                            .processDefinitionId("process")
                            .addCompletedElements("task1", "end")
                            .addTakenSequenceFlows("flow2")
                            .coverage(0.6)
                            .build())
                    .build())
            .build();

    // when
    final CoverageReport report =
        CoverageReportCreator.createAggregatedCoverageReport(
            Collections.singletonList(suite),
            Collections.singletonList(processModel),
            Collections.emptyList());

    // then: the run still reports what each instance covered, in the order they ran
    assertThat(report.getSuites().get(0).getRuns().get(0).getProcessCoverages())
        .extracting(ProcessCoverage::getCoverage)
        .containsExactly(0.4, 0.6);
  }

  @Test
  void shouldSetSuiteAggregatedCoverageFromAllRuns() {
    // given: suite with two runs each covering a different process
    final ProcessModel processModelA =
        ProcessModelFixtures.modelOf(
            "process-a",
            Bpmn.createExecutableProcess("process-a")
                .startEvent("start")
                .sequenceFlowId("flow")
                .endEvent("end")
                .done());
    final DecisionModel decisionModel =
        ImmutableDecisionModel.builder()
            .decisionDefinitionId("decision")
            .totalRuleCount(2)
            .version("1")
            .xml("<dmn>decision</dmn>")
            .build();

    final CoverageSuiteReport suite =
        ImmutableCoverageSuiteReport.builder()
            .id("suite")
            .name("Suite")
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run-1")
                    .addProcessCoverages(
                        ImmutableProcessCoverage.builder()
                            .processDefinitionId("process-a")
                            .addCompletedElements("start", "end")
                            .addTakenSequenceFlows("flow")
                            .coverage(1.0)
                            .build())
                    .addDecisionCoverages(
                        ImmutableDecisionCoverage.builder()
                            .decisionDefinitionId("decision")
                            .addMatchedRuleIds("rule-1")
                            .addMatchedRuleIndices(1)
                            .coverage(0.5)
                            .build())
                    .build())
            .build();

    // when
    final CoverageReport report =
        CoverageReportCreator.createAggregatedCoverageReport(
            Collections.singletonList(suite),
            Collections.singletonList(processModelA),
            Collections.singletonList(decisionModel));

    // then: suite has aggregated process and decision coverage
    assertThat(report.getSuites()).hasSize(1);
    assertThat(report.getSuites().get(0).getProcessCoverages()).hasSize(1);
    assertThat(report.getSuites().get(0).getProcessCoverages().get(0).getCoverage()).isEqualTo(1.0);
    assertThat(report.getSuites().get(0).getDecisionCoverages()).hasSize(1);
    assertThat(report.getSuites().get(0).getDecisionCoverages().get(0).getCoverage())
        .isEqualTo(0.5);
  }
}
