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

import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.process.test.api.coverage.model.DecisionCoverage;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableDecisionModel;
import io.camunda.process.test.api.coverage.model.ImmutableProcessModel;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.impl.coverage.core.CoverageCreator;
import io.camunda.process.test.impl.coverage.core.DecisionCoverageCreator;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageDecisionInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessInstanceData;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CoverageCreatorTest {

  @Test
  void shouldCreateCoverageFromSnapshotData() {
    // given
    final ProcessInstance processInstance = mock(ProcessInstance.class);
    final ElementInstance elementInstance = mock(ElementInstance.class);
    final ProcessInstanceSequenceFlow flow = mock(ProcessInstanceSequenceFlow.class);
    final ProcessModel processModel =
        ImmutableProcessModel.builder()
            .processDefinitionId("process")
            .totalElementCount(2)
            .version("1")
            .xml(
                Bpmn.convertToString(
                    Bpmn.createExecutableProcess("process")
                        .startEvent("start")
                        .endEvent("end")
                        .done()))
            .build();

    when(processInstance.getProcessInstanceKey()).thenReturn(1L);
    when(processInstance.getProcessDefinitionId()).thenReturn("process");
    when(elementInstance.getElementId()).thenReturn("task");
    when(elementInstance.getType()).thenReturn(ElementInstanceType.SERVICE_TASK);
    when(elementInstance.getState()).thenReturn(ElementInstanceState.COMPLETED);
    when(flow.getElementId()).thenReturn("flow");

    final ImmutableCoverageProcessInstanceData processInstanceResult =
        ImmutableCoverageProcessInstanceData.builder()
            .processInstance(processInstance)
            .addElementInstances(elementInstance)
            .addSequenceFlows(flow)
            .build();

    // when
    final var coverage = CoverageCreator.createCoverage(processInstanceResult, processModel);

    // then
    assertThat(coverage.getProcessDefinitionId()).isEqualTo("process");
    assertThat(coverage.getCompletedElements()).containsExactly("task");
    assertThat(coverage.getTakenSequenceFlows()).containsExactly("flow");
    assertThat(coverage.getCoverage()).isEqualTo(1.0);
  }

  @Test
  void shouldCreateDecisionCoverageFromSnapshotData() {
    // given
    final DecisionInstance decisionInstance = mock(DecisionInstance.class);
    final MatchedDecisionRule rule1 = mock(MatchedDecisionRule.class);
    final MatchedDecisionRule rule2 = mock(MatchedDecisionRule.class);
    final DecisionModel model =
        ImmutableDecisionModel.builder()
            .decisionDefinitionId("decision")
            .totalRuleCount(2)
            .version("1")
            .xml("<dmn />")
            .build();

    when(decisionInstance.getDecisionInstanceId()).thenReturn("instance-1");
    when(decisionInstance.getDecisionDefinitionId()).thenReturn("decision");
    when(rule1.getRuleId()).thenReturn("rule-1");
    when(rule1.getRuleIndex()).thenReturn(1);
    when(rule2.getRuleId()).thenReturn("rule-2");
    when(rule2.getRuleIndex()).thenReturn(2);
    when(decisionInstance.getMatchedRules()).thenReturn(Arrays.asList(rule1, rule2));

    final ImmutableCoverageDecisionInstanceData decisionInstanceResult =
        ImmutableCoverageDecisionInstanceData.builder().decisionInstance(decisionInstance).build();

    // when
    final DecisionCoverage coverage =
        DecisionCoverageCreator.createCoverage(decisionInstanceResult, model);

    // then
    assertThat(coverage.getDecisionDefinitionId()).isEqualTo("decision");
    assertThat(coverage.getMatchedRuleIds()).containsExactly("rule-1", "rule-2");
    assertThat(coverage.getMatchedRuleIndices()).containsExactly(1, 2);
    assertThat(coverage.getCoverage()).isEqualTo(1.0);
  }
}
