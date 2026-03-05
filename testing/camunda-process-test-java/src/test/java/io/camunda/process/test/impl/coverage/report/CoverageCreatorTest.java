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
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.coverage.core.CoverageCreator;
import io.camunda.process.test.impl.coverage.core.ModelCreator;
import io.camunda.process.test.impl.coverage.model.Coverage;
import io.camunda.process.test.impl.coverage.model.Model;
import io.camunda.process.test.utils.ElementInstanceBuilder;
import io.camunda.process.test.utils.ProcessDefinitionBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import io.camunda.process.test.utils.ProcessInstanceSequenceFlowBuilder;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CoverageCreatorTest {

  private static final String PROCESS_DEFINITION_ID = "process";

  private static final long PROCESS_INSTANCE_KEY = 100L;

  private static final String ELEMENT_ID_START_EVENT = "start";
  private static final String ELEMENT_ID_END_EVENT = "end";
  private static final String ELEMENT_ID_SEQUENCE_FLOW = "from_start";

  private static final String ELEMENT_ID_AD_HOC_SUB_PROCESS = "adHocSubProcess";
  private static final String ELEMENT_ID_AD_HOC_SUB_PROCESS_INNER_INSTANCE =
      ELEMENT_ID_AD_HOC_SUB_PROCESS + ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
  private static final String ELEMENT_ID_TASK_1 = "task_1";
  private static final String ELEMENT_ID_TASK_2 = "task_2";

  private static final String ELEMENT_ID_SUB_PROCESS = "subProcess";
  private static final String ELEMENT_ID_SUB_PROCESS_START_EVENT = "subProcess_start";
  private static final String ELEMENT_ID_SUB_PROCESS_SEQUENCE_FLOW_1 = "subProcess_from_start";
  private static final String ELEMENT_ID_SUB_PROCESS_SEQUENCE_FLOW_2 = "subProcess_from_task_1";

  private static final BpmnModelInstance PROCESS_WITH_START_AND_END_EVENT =
      Bpmn.createExecutableProcess(PROCESS_DEFINITION_ID)
          .startEvent(ELEMENT_ID_START_EVENT)
          .sequenceFlowId(ELEMENT_ID_SEQUENCE_FLOW)
          .endEvent(ELEMENT_ID_END_EVENT)
          .done();

  private static final BpmnModelInstance PROCESS_WITH_AD_HOC_SUB_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_DEFINITION_ID)
          .startEvent(ELEMENT_ID_START_EVENT)
          .sequenceFlowId(ELEMENT_ID_SEQUENCE_FLOW)
          .adHocSubProcess(
              ELEMENT_ID_AD_HOC_SUB_PROCESS,
              adHocSubProcess -> {
                adHocSubProcess.task(ELEMENT_ID_TASK_1);
                adHocSubProcess.task(ELEMENT_ID_TASK_2);
              })
          .done();

  private static final BpmnModelInstance PROCESS_WITH_MULTI_INSTANCE =
      Bpmn.createExecutableProcess(PROCESS_DEFINITION_ID)
          .startEvent(ELEMENT_ID_START_EVENT)
          .sequenceFlowId(ELEMENT_ID_SEQUENCE_FLOW)
          .subProcess(
              ELEMENT_ID_SUB_PROCESS,
              subProcess ->
                  subProcess
                      .multiInstance(m -> m.zeebeInputCollectionExpression("[1]"))
                      .embeddedSubProcess()
                      .startEvent(ELEMENT_ID_SUB_PROCESS_START_EVENT)
                      .sequenceFlowId(ELEMENT_ID_SUB_PROCESS_SEQUENCE_FLOW_1)
                      .task(ELEMENT_ID_TASK_1)
                      .sequenceFlowId(ELEMENT_ID_SUB_PROCESS_SEQUENCE_FLOW_2)
                      .task(ELEMENT_ID_TASK_2))
          .done();

  private static final ProcessDefinition PROCESS_DEFINITION =
      ProcessDefinitionBuilder.newProcessDefinition(PROCESS_DEFINITION_ID).build();

  private static final ProcessInstance PROCESS_INSTANCE =
      ProcessInstanceBuilder.newCompletedProcessInstance(PROCESS_INSTANCE_KEY)
          .setProcessDefinitionId(PROCESS_DEFINITION_ID)
          .build();

  @Mock private CamundaDataSource dataSource;

  private Model createModel(final BpmnModelInstance process) {
    when(dataSource.getProcessDefinitionXmlByProcessDefinitionId(PROCESS_DEFINITION_ID))
        .thenReturn(Bpmn.convertToString(process));

    when(dataSource.findProcessDefinitionByProcessDefinitionId(PROCESS_DEFINITION_ID))
        .thenReturn(PROCESS_DEFINITION);

    return ModelCreator.createModel(dataSource, PROCESS_DEFINITION_ID);
  }

  @Test
  void shouldReturnCompletedElements() {
    // given
    final Model model = createModel(PROCESS_WITH_START_AND_END_EVENT);

    when(dataSource.findElementInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Arrays.asList(
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_START_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.START_EVENT)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_END_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.END_EVENT)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_END_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.END_EVENT)
                    .build()));

    // when
    final Coverage coverage = CoverageCreator.createCoverage(dataSource, PROCESS_INSTANCE, model);

    // then
    assertThat(coverage.getCompletedElements())
        .containsExactly(ELEMENT_ID_START_EVENT, ELEMENT_ID_END_EVENT);
  }

  @Test
  void shouldReturnTakenSequenceFlows() {
    // given
    final Model model = createModel(PROCESS_WITH_START_AND_END_EVENT);

    when(dataSource.findSequenceFlowsByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Arrays.asList(
                ProcessInstanceSequenceFlowBuilder.newSequenceFlow(
                        ELEMENT_ID_SEQUENCE_FLOW, PROCESS_INSTANCE_KEY)
                    .build(),
                ProcessInstanceSequenceFlowBuilder.newSequenceFlow(
                        ELEMENT_ID_SEQUENCE_FLOW, PROCESS_INSTANCE_KEY)
                    .build()));

    // when
    final Coverage coverage = CoverageCreator.createCoverage(dataSource, PROCESS_INSTANCE, model);

    // then
    assertThat(coverage.getTakenSequenceFlows()).containsExactly(ELEMENT_ID_SEQUENCE_FLOW);
  }

  @Test
  void shouldReturnFullCoverage() {
    // given
    final Model model = createModel(PROCESS_WITH_START_AND_END_EVENT);

    when(dataSource.findElementInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Arrays.asList(
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_START_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.START_EVENT)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_END_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.END_EVENT)
                    .build()));

    when(dataSource.findSequenceFlowsByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceSequenceFlowBuilder.newSequenceFlow(
                        ELEMENT_ID_SEQUENCE_FLOW, PROCESS_INSTANCE_KEY)
                    .build()));

    // when
    final Coverage coverage = CoverageCreator.createCoverage(dataSource, PROCESS_INSTANCE, model);

    // then
    assertThat(coverage.getCoverage()).isEqualTo(1.0);
  }

  @Test
  void shouldReturnPartialCoverage() {
    // given
    final Model model = createModel(PROCESS_WITH_START_AND_END_EVENT);

    when(dataSource.findElementInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Collections.singletonList(
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_START_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.START_EVENT)
                    .build()));

    when(dataSource.findSequenceFlowsByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(Collections.emptyList());

    // when
    final Coverage coverage = CoverageCreator.createCoverage(dataSource, PROCESS_INSTANCE, model);

    // then
    assertThat(coverage.getCoverage()).isBetween(0.33, 0.34);
  }

  @Test
  void shouldNotReturnAdHocSubProcessInnerInstances() {
    // given
    final Model model = createModel(PROCESS_WITH_AD_HOC_SUB_PROCESS);

    when(dataSource.findElementInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Arrays.asList(
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_START_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.START_EVENT)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_AD_HOC_SUB_PROCESS, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.AD_HOC_SUB_PROCESS)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_AD_HOC_SUB_PROCESS_INNER_INSTANCE, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_TASK_1, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.TASK)
                    .build()));

    // when
    final Coverage coverage = CoverageCreator.createCoverage(dataSource, PROCESS_INSTANCE, model);

    // then
    assertThat(coverage.getCompletedElements())
        .containsExactly(ELEMENT_ID_START_EVENT, ELEMENT_ID_AD_HOC_SUB_PROCESS, ELEMENT_ID_TASK_1)
        .doesNotContain(ELEMENT_ID_AD_HOC_SUB_PROCESS_INNER_INSTANCE);
  }

  @Test
  void shouldReturnFullCoverageOfAdHocSubProcess() {
    // given
    final Model model = createModel(PROCESS_WITH_AD_HOC_SUB_PROCESS);

    when(dataSource.findElementInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Arrays.asList(
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_START_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.START_EVENT)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_AD_HOC_SUB_PROCESS, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.AD_HOC_SUB_PROCESS)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_AD_HOC_SUB_PROCESS_INNER_INSTANCE, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_TASK_1, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.TASK)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_TASK_2, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.TASK)
                    .build()));

    when(dataSource.findSequenceFlowsByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceSequenceFlowBuilder.newSequenceFlow(
                        ELEMENT_ID_SEQUENCE_FLOW, PROCESS_INSTANCE_KEY)
                    .build()));

    // when
    final Coverage coverage = CoverageCreator.createCoverage(dataSource, PROCESS_INSTANCE, model);

    // then
    assertThat(coverage.getCoverage()).isEqualTo(1.0);
  }

  @Test
  void shouldReturnPartialCoverageOfAdHocSubProcess() {
    // given
    final Model model = createModel(PROCESS_WITH_AD_HOC_SUB_PROCESS);

    when(dataSource.findElementInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Arrays.asList(
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_START_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.START_EVENT)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_AD_HOC_SUB_PROCESS, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.AD_HOC_SUB_PROCESS)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_AD_HOC_SUB_PROCESS_INNER_INSTANCE, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_TASK_1, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.TASK)
                    .build()));

    when(dataSource.findSequenceFlowsByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceSequenceFlowBuilder.newSequenceFlow(
                        ELEMENT_ID_SEQUENCE_FLOW, PROCESS_INSTANCE_KEY)
                    .build()));

    // when
    final Coverage coverage = CoverageCreator.createCoverage(dataSource, PROCESS_INSTANCE, model);

    // then
    assertThat(coverage.getCoverage()).isEqualTo(0.8);
  }

  @Test
  void shouldReturnFullCoverageOfMultiInstance() {
    // given
    final Model model = createModel(PROCESS_WITH_MULTI_INSTANCE);

    when(dataSource.findElementInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Arrays.asList(
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_START_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.START_EVENT)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_SUB_PROCESS, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.MULTI_INSTANCE_BODY)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_SUB_PROCESS, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.SUB_PROCESS)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_SUB_PROCESS_START_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.SUB_PROCESS)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_TASK_1, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.TASK)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_TASK_2, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.TASK)
                    .build()));

    when(dataSource.findSequenceFlowsByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Arrays.asList(
                ProcessInstanceSequenceFlowBuilder.newSequenceFlow(
                        ELEMENT_ID_SEQUENCE_FLOW, PROCESS_INSTANCE_KEY)
                    .build(),
                ProcessInstanceSequenceFlowBuilder.newSequenceFlow(
                        ELEMENT_ID_SUB_PROCESS_SEQUENCE_FLOW_1, PROCESS_INSTANCE_KEY)
                    .build(),
                ProcessInstanceSequenceFlowBuilder.newSequenceFlow(
                        ELEMENT_ID_SUB_PROCESS_SEQUENCE_FLOW_2, PROCESS_INSTANCE_KEY)
                    .build()));

    // when
    final Coverage coverage = CoverageCreator.createCoverage(dataSource, PROCESS_INSTANCE, model);

    // then
    assertThat(coverage.getCoverage()).isEqualTo(1.0);
  }

  @Test
  void shouldReturnPartialCoverageOfMultiInstance() {
    // given
    final Model model = createModel(PROCESS_WITH_MULTI_INSTANCE);

    when(dataSource.findElementInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Arrays.asList(
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_START_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.START_EVENT)
                    .build(),
                ElementInstanceBuilder.newActiveElementInstance(
                        ELEMENT_ID_SUB_PROCESS, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.MULTI_INSTANCE_BODY)
                    .build(),
                ElementInstanceBuilder.newActiveElementInstance(
                        ELEMENT_ID_SUB_PROCESS, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.SUB_PROCESS)
                    .build(),
                ElementInstanceBuilder.newCompletedElementInstance(
                        ELEMENT_ID_SUB_PROCESS_START_EVENT, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.SUB_PROCESS)
                    .build(),
                ElementInstanceBuilder.newActiveElementInstance(
                        ELEMENT_ID_TASK_1, PROCESS_INSTANCE_KEY)
                    .setType(ElementInstanceType.TASK)
                    .build()));

    when(dataSource.findSequenceFlowsByProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .thenReturn(
            Arrays.asList(
                ProcessInstanceSequenceFlowBuilder.newSequenceFlow(
                        ELEMENT_ID_SEQUENCE_FLOW, PROCESS_INSTANCE_KEY)
                    .build(),
                ProcessInstanceSequenceFlowBuilder.newSequenceFlow(
                        ELEMENT_ID_SUB_PROCESS_SEQUENCE_FLOW_1, PROCESS_INSTANCE_KEY)
                    .build()));

    // when
    final Coverage coverage = CoverageCreator.createCoverage(dataSource, PROCESS_INSTANCE, model);

    // then
    assertThat(coverage.getCoverage()).isEqualTo(0.75);
  }
}
