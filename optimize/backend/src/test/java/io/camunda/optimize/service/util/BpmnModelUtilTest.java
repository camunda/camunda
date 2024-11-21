/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.util.ZeebeBpmnModels.START_EVENT;
import static io.camunda.optimize.util.ZeebeBpmnModels.USER_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleUserTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;

public class BpmnModelUtilTest {

  public static final String CUSTOMER_ONBOARDING = "CustomerOnboarding";

  public static final String SIMPLE_SERVICE_TASK_PROCESS =
      Bpmn.convertToString(createSimpleServiceTaskProcess(CUSTOMER_ONBOARDING));

  @Test
  void shouldParseBpmnModel() {
    final BpmnModelInstance modelInstance =
        BpmnModelUtil.parseBpmnModel(SIMPLE_SERVICE_TASK_PROCESS);
    assertThat(modelInstance).isNotNull();
  }

  @Test
  void shouldExtractFlowNodeData() {
    final List<FlowNodeDataDto> flowNodeData =
        BpmnModelUtil.extractFlowNodeData(SIMPLE_SERVICE_TASK_PROCESS);
    assertThat(flowNodeData).isNotNull();
    assertThat(flowNodeData.isEmpty()).isFalse();
    for (FlowNodeDataDto node : flowNodeData) {
      assertThat(node.getId()).isNotNull();
    }
  }

  @Test
  void shouldExtractUserTaskNames() {
    final String bpmnModelInstance =
        Bpmn.convertToString(createSimpleUserTaskProcess(CUSTOMER_ONBOARDING));
    final Map<String, String> userTaskNames = BpmnModelUtil.extractUserTaskNames(bpmnModelInstance);
    assertThat(userTaskNames).isNotNull();
    assertThat(userTaskNames.isEmpty()).isFalse();

    assertThat(userTaskNames.containsKey(USER_TASK)).isTrue();
  }

  @Test
  void shouldExtractProcessDefinitionName() {
    final Optional<String> processName =
        BpmnModelUtil.extractProcessDefinitionName(
            CUSTOMER_ONBOARDING, SIMPLE_SERVICE_TASK_PROCESS);
    assertThat(processName.isPresent()).isTrue();
    assertThat(CUSTOMER_ONBOARDING).isEqualTo(processName.get());
  }

  @Test
  void shouldExtractFlowNodeNames() {
    final List<FlowNodeDataDto> flowNodeData =
        BpmnModelUtil.extractFlowNodeData(SIMPLE_SERVICE_TASK_PROCESS);
    final Map<String, String> flowNodeNames = BpmnModelUtil.extractFlowNodeNames(flowNodeData);
    assertThat(flowNodeNames).isNotNull();
    assertThat(flowNodeNames.isEmpty()).isFalse();

    assertThat(flowNodeNames.containsKey(START_EVENT)).isTrue();
    assertThat(START_EVENT).isEqualTo(flowNodeNames.get(START_EVENT));
  }

  @Test
  void shouldGetCollapsedSubprocessElementIds() {
    final Set<String> collapsedSubprocessIds =
        BpmnModelUtil.getCollapsedSubprocessElementIds(SIMPLE_SERVICE_TASK_PROCESS);
    assertThat(collapsedSubprocessIds).isNotNull();
    assertThat(collapsedSubprocessIds.isEmpty()).isTrue();
  }
}
