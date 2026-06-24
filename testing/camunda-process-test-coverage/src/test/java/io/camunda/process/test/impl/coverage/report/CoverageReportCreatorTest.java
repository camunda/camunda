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
import io.camunda.process.test.api.coverage.model.ImmutableProcessModel;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CoverageReportCreatorTest {

  @Test
  void shouldCreateAggregatedCoverageReportWithModels() {
    // given
    final ProcessModel processModel =
        ImmutableProcessModel.builder()
            .processDefinitionId("process")
            .totalElementCount(2)
            .version("1")
            .xml("<bpmn>process</bpmn>")
            .build();
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
        .satisfies(model -> assertThat(model.getXml()).isEqualTo("<bpmn>process</bpmn>"));
    assertThat(report.getDecisionModels())
        .singleElement()
        .satisfies(model -> assertThat(model.getXml()).isEqualTo("<dmn>decision</dmn>"));
  }

  @Test
  void shouldAggregateMultipleSuitesIntoSingleReport() {
    // given: two suites each covering a different process
    final ProcessModel processModelA =
        ImmutableProcessModel.builder()
            .processDefinitionId("process-a")
            .totalElementCount(2)
            .version("1")
            .xml("<bpmn>process-a</bpmn>")
            .build();
    final ProcessModel processModelB =
        ImmutableProcessModel.builder()
            .processDefinitionId("process-b")
            .totalElementCount(4)
            .version("1")
            .xml("<bpmn>process-b</bpmn>")
            .build();

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
        ImmutableProcessModel.builder()
            .processDefinitionId("process")
            .totalElementCount(4)
            .version("1")
            .xml("<bpmn>process</bpmn>")
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
                            .processDefinitionId("process")
                            .addCompletedElements("element1")
                            .addTakenSequenceFlows("flow1")
                            .coverage(0.5)
                            .build())
                    .build())
            .addRuns(
                ImmutableCoverageRunReport.builder()
                    .name("run-2")
                    .addProcessCoverages(
                        ImmutableProcessCoverage.builder()
                            .processDefinitionId("process")
                            .addCompletedElements("element2")
                            .addTakenSequenceFlows("flow2")
                            .coverage(0.5)
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

    // and the aggregated coverage across runs covers all 4 elements → 100%
    assertThat(report.getProcessCoverages()).hasSize(1);
    assertThat(report.getProcessCoverages().get(0).getCoverage()).isEqualTo(1.0);
    assertThat(report.getProcessCoverages().get(0).getCompletedElements())
        .containsExactlyInAnyOrder("element1", "element2");
  }

  @Test
  void shouldSetSuiteAggregatedCoverageFromAllRuns() {
    // given: suite with two runs each covering a different process
    final ProcessModel processModelA =
        ImmutableProcessModel.builder()
            .processDefinitionId("process-a")
            .totalElementCount(2)
            .version("1")
            .xml("<bpmn>process-a</bpmn>")
            .build();
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
                            .addCompletedElements("task")
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
