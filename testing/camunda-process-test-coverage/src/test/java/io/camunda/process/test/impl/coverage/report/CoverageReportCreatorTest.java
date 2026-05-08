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
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableCoverage;
import io.camunda.process.test.api.coverage.model.ImmutableDecisionCoverage;
import io.camunda.process.test.api.coverage.model.ImmutableDecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableModel;
import io.camunda.process.test.api.coverage.model.ImmutableRun;
import io.camunda.process.test.api.coverage.model.ImmutableSuite;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.api.coverage.model.Suite;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CoverageReportCreatorTest {

  @Test
  void shouldCreateAggregatedCoverageReportWithDefinitions() {
    // given
    final Model model =
        ImmutableModel.builder()
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
    final Suite suite =
        ImmutableSuite.builder()
            .id("suite")
            .name("Suite")
            .addRuns(
                ImmutableRun.builder()
                    .name("run")
                    .addCoverages(
                        ImmutableCoverage.builder()
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
            Collections.singletonList(model),
            Collections.singletonList(decisionModel));

    // then
    assertThat(report.getSuites()).hasSize(1);
    assertThat(report.getCoverages()).hasSize(1);
    assertThat(report.getDecisionCoverages()).hasSize(1);
    assertThat(report.getDefinitions()).containsEntry("process", "<bpmn>process</bpmn>");
    assertThat(report.getDecisionDefinitions()).containsEntry("decision", "<dmn>decision</dmn>");
  }
}
