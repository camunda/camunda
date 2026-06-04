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
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.impl.coverage.CoverageCollector;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageDecisionInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageTestData;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class CoverageReportCollectorBuilderTest {

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
        coverageCollector.collectTestRunCoverage(getClass(), "run-1", testResults);

    // then
    assertThat(report.getSuites())
        .singleElement()
        .satisfies(suite -> assertThat(suite.getRuns()).hasSize(1));
    assertThat(report.getProcessCoverages()).isEmpty();
    assertThat(report.getDecisionCoverages()).isEmpty();
  }
}
