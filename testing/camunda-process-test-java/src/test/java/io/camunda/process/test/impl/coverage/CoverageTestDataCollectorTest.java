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
package io.camunda.process.test.impl.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoverageTestDataCollectorTest {

  @Mock private CamundaDataSource dataSource;
  @Mock private ProcessInstance processInstanceA;
  @Mock private ProcessInstance processInstanceB;
  @Mock private ElementInstance elementInstanceA;
  @Mock private ElementInstance elementInstanceB;
  @Mock private ProcessInstanceSequenceFlow sequenceFlowA;
  @Mock private ProcessInstanceSequenceFlow sequenceFlowB;
  @Mock private ProcessDefinition processDefinition;
  @Mock private ProcessDefinition otherProcessDefinition;
  @Mock private DecisionInstance decisionInstanceSummaryA;
  @Mock private DecisionInstance decisionInstanceSummaryB;
  @Mock private DecisionInstance decisionInstanceDetailA;
  @Mock private DecisionInstance decisionInstanceDetailB;
  @Mock private DecisionDefinition decisionDefinition;
  @Mock private DecisionDefinition otherDecisionDefinition;

  @Test
  void shouldCollectAllDataFromDataSource() {
    // given
    when(processInstanceA.getProcessInstanceKey()).thenReturn(100L);
    when(processInstanceB.getProcessInstanceKey()).thenReturn(200L);
    when(processInstanceA.getProcessDefinitionKey()).thenReturn(11L);
    when(processInstanceB.getProcessDefinitionKey()).thenReturn(11L);

    when(dataSource.findProcessInstances())
        .thenReturn(Arrays.asList(processInstanceA, processInstanceB));
    when(dataSource.findElementInstancesByProcessInstanceKey(100L))
        .thenReturn(java.util.Collections.singletonList(elementInstanceA));
    when(dataSource.findElementInstancesByProcessInstanceKey(200L))
        .thenReturn(java.util.Collections.singletonList(elementInstanceB));
    when(dataSource.findSequenceFlowsByProcessInstanceKey(100L))
        .thenReturn(java.util.Collections.singletonList(sequenceFlowA));
    when(dataSource.findSequenceFlowsByProcessInstanceKey(200L))
        .thenReturn(java.util.Collections.singletonList(sequenceFlowB));

    when(dataSource.getProcessDefinitionByProcessDefinitionKey(11L)).thenReturn(processDefinition);
    when(dataSource.getProcessDefinitionXmlByProcessDefinitionKey(11L))
        .thenReturn("<bpmn>process-1</bpmn>");

    when(dataSource.findDecisionInstances(any()))
        .thenReturn(Arrays.asList(decisionInstanceSummaryA, decisionInstanceSummaryB));
    when(decisionInstanceSummaryA.getDecisionInstanceId()).thenReturn("di-1");
    when(decisionInstanceSummaryB.getDecisionInstanceId()).thenReturn("di-2");

    when(dataSource.getDecisionInstance("di-1")).thenReturn(decisionInstanceDetailA);
    when(dataSource.getDecisionInstance("di-2")).thenReturn(decisionInstanceDetailB);
    when(decisionInstanceSummaryA.getDecisionDefinitionKey()).thenReturn(21L);
    when(decisionInstanceSummaryB.getDecisionDefinitionKey()).thenReturn(21L);

    when(dataSource.getDecisionDefinitionByDecisionDefinitionKey(21L))
        .thenReturn(decisionDefinition);
    when(dataSource.getDecisionDefinitionXmlByDecisionDefinitionKey(21L))
        .thenReturn("<dmn>decision-1</dmn>");

    // when
    final CoverageTestData data = CoverageTestDataCollector.collectData(dataSource);

    // then
    assertThat(data.getProcessInstanceData()).hasSize(2);
    assertThat(data.getProcessInstanceData())
        .extracting(entry -> entry.getProcessInstance())
        .containsExactly(processInstanceA, processInstanceB);
    assertThat(data.getProcessInstanceData().get(0).getElementInstances())
        .containsExactly(elementInstanceA);
    assertThat(data.getProcessInstanceData().get(1).getElementInstances())
        .containsExactly(elementInstanceB);
    assertThat(data.getProcessInstanceData().get(0).getSequenceFlows())
        .containsExactly(sequenceFlowA);
    assertThat(data.getProcessInstanceData().get(1).getSequenceFlows())
        .containsExactly(sequenceFlowB);

    assertThat(data.getProcessDefinitionData())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.getProcessDefinition()).isSameAs(processDefinition);
              assertThat(entry.getXml()).isEqualTo("<bpmn>process-1</bpmn>");
            });

    assertThat(data.getDecisionInstanceData())
        .extracting(entry -> entry.getDecisionInstance())
        .containsExactly(decisionInstanceDetailA, decisionInstanceDetailB);
    assertThat(data.getDecisionDefinitionData())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.getDecisionDefinition()).isSameAs(decisionDefinition);
              assertThat(entry.getXml()).isEqualTo("<dmn>decision-1</dmn>");
            });

    verify(dataSource, times(1)).getProcessDefinitionByProcessDefinitionKey(11L);
    verify(dataSource, times(1)).getProcessDefinitionXmlByProcessDefinitionKey(11L);
    verify(dataSource, times(1)).getDecisionDefinitionByDecisionDefinitionKey(21L);
    verify(dataSource, times(1)).getDecisionDefinitionXmlByDecisionDefinitionKey(21L);
    verify(dataSource).getDecisionInstance("di-1");
    verify(dataSource).getDecisionInstance("di-2");
  }

  /**
   * A test can run several deployments of one process definition id, for example when a mock
   * deploys a stub of a process that is also deployed for real. Each instance is covered by the
   * deployment it ran, so the data of every deployment is needed, not only of the newest one.
   */
  @Test
  void shouldCollectEveryDeploymentThatRan() {
    // given: two instances of one process definition id, of different deployments
    when(processInstanceA.getProcessInstanceKey()).thenReturn(100L);
    when(processInstanceB.getProcessInstanceKey()).thenReturn(200L);
    when(processInstanceA.getProcessDefinitionKey()).thenReturn(11L);
    when(processInstanceB.getProcessDefinitionKey()).thenReturn(12L);

    when(dataSource.findProcessInstances())
        .thenReturn(Arrays.asList(processInstanceA, processInstanceB));

    when(dataSource.getProcessDefinitionByProcessDefinitionKey(11L)).thenReturn(processDefinition);
    when(dataSource.getProcessDefinitionByProcessDefinitionKey(12L))
        .thenReturn(otherProcessDefinition);
    when(dataSource.getProcessDefinitionXmlByProcessDefinitionKey(11L))
        .thenReturn("<bpmn>deployment-11</bpmn>");
    when(dataSource.getProcessDefinitionXmlByProcessDefinitionKey(12L))
        .thenReturn("<bpmn>deployment-12</bpmn>");

    // when
    final CoverageTestData data = CoverageTestDataCollector.collectData(dataSource);

    // then
    assertThat(data.getProcessDefinitionData())
        .extracting(entry -> entry.getProcessDefinition(), entry -> entry.getXml())
        .containsExactly(
            tuple(processDefinition, "<bpmn>deployment-11</bpmn>"),
            tuple(otherProcessDefinition, "<bpmn>deployment-12</bpmn>"));
  }

  /**
   * A test can evaluate several deployments of one decision definition id, for example when suites
   * deploy fixtures that differ in their rules. Each evaluation is covered by the table it ran.
   */
  @Test
  void shouldCollectEveryDeploymentThatWasEvaluated() {
    // given: two evaluations of one decision definition id, of different deployments
    when(dataSource.findProcessInstances()).thenReturn(java.util.Collections.emptyList());
    when(dataSource.findDecisionInstances(any()))
        .thenReturn(Arrays.asList(decisionInstanceSummaryA, decisionInstanceSummaryB));
    when(decisionInstanceSummaryA.getDecisionInstanceId()).thenReturn("di-1");
    when(decisionInstanceSummaryB.getDecisionInstanceId()).thenReturn("di-2");
    when(dataSource.getDecisionInstance("di-1")).thenReturn(decisionInstanceDetailA);
    when(dataSource.getDecisionInstance("di-2")).thenReturn(decisionInstanceDetailB);
    when(decisionInstanceSummaryA.getDecisionDefinitionKey()).thenReturn(21L);
    when(decisionInstanceSummaryB.getDecisionDefinitionKey()).thenReturn(22L);

    when(dataSource.getDecisionDefinitionByDecisionDefinitionKey(21L))
        .thenReturn(decisionDefinition);
    when(dataSource.getDecisionDefinitionByDecisionDefinitionKey(22L))
        .thenReturn(otherDecisionDefinition);
    when(dataSource.getDecisionDefinitionXmlByDecisionDefinitionKey(21L))
        .thenReturn("<dmn>deployment-21</dmn>");
    when(dataSource.getDecisionDefinitionXmlByDecisionDefinitionKey(22L))
        .thenReturn("<dmn>deployment-22</dmn>");

    // when
    final CoverageTestData data = CoverageTestDataCollector.collectData(dataSource);

    // then
    assertThat(data.getDecisionDefinitionData())
        .extracting(entry -> entry.getDecisionDefinition(), entry -> entry.getXml())
        .containsExactly(
            tuple(decisionDefinition, "<dmn>deployment-21</dmn>"),
            tuple(otherDecisionDefinition, "<dmn>deployment-22</dmn>"));
  }
}
