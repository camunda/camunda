/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.util.BpmnModelUtil.parseBpmnModel;
import static io.camunda.optimize.util.ZeebeBpmnModels.END_EVENT;
import static io.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.START_EVENT;
import static io.camunda.optimize.util.ZeebeBpmnModels.USER_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleUserTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

public class BpmnModelUtilTest {

  public static final String PROCESS_NAME = "processName";
  public static final String END_EVENT_TYPE = "endEvent";
  public static final String SERVICE_TASK_TYPE = "serviceTask";

  public static final String SIMPLE_SERVICE_TASK_PROCESS =
      Bpmn.convertToString(createSimpleServiceTaskProcess(PROCESS_NAME));

  @Test
  void shouldParseBpmnModel() {
    // when
    final BpmnModelInstance modelInstance = parseBpmnModel(SIMPLE_SERVICE_TASK_PROCESS);

    // then
    assertThat(modelInstance).isNotNull();
  }

  @Test
  void shouldExtractFlowNodeData() {
    // when
    final List<FlowNodeDataDto> flowNodeData =
        BpmnModelUtil.extractFlowNodeData(SIMPLE_SERVICE_TASK_PROCESS);

    // then
    assertThat(flowNodeData)
        .isNotNull()
        .isNotEmpty()
        .extracting(FlowNodeDataDto::getId, FlowNodeDataDto::getType, FlowNodeDataDto::getName)
        .containsExactlyInAnyOrder(
            Tuple.tuple(START_EVENT, START_EVENT, START_EVENT),
            Tuple.tuple(SERVICE_TASK, SERVICE_TASK_TYPE, SERVICE_TASK),
            Tuple.tuple(END_EVENT, END_EVENT_TYPE, null));
  }

  @Test
  void shouldExtractUserTaskNames() {
    // when
    final String bpmnModelInstance =
        Bpmn.convertToString(createSimpleUserTaskProcess(PROCESS_NAME));

    // when
    final Map<String, String> userTaskNames = BpmnModelUtil.extractUserTaskNames(bpmnModelInstance);
    assertThat(userTaskNames)
        .isNotNull()
        .isNotEmpty()
        .containsKey(USER_TASK)
        .containsValue(USER_TASK);
  }

  @Test
  void shouldExtractProcessDefinitionName() {
    // when
    final Optional<String> processName =
        BpmnModelUtil.extractProcessDefinitionName(PROCESS_NAME, SIMPLE_SERVICE_TASK_PROCESS);

    // then
    assertThat(processName).isPresent().get().isEqualTo(PROCESS_NAME);
  }

  @Test
  void shouldNotExtractNameForUnkownProcessDefinitionKey() {
    // when
    final Optional<String> processName =
        BpmnModelUtil.extractProcessDefinitionName("someUnknownKey", SIMPLE_SERVICE_TASK_PROCESS);

    // then
    assertThat(processName).isNotPresent();
  }

  @Test
  void shouldExtractFlowNodeNames() {
    // given
    final List<FlowNodeDataDto> flowNodeData =
        BpmnModelUtil.extractFlowNodeData(SIMPLE_SERVICE_TASK_PROCESS);

    // when
    final Map<String, String> flowNodeNames = BpmnModelUtil.extractFlowNodeNames(flowNodeData);

    // then
    assertThat(flowNodeNames)
        .isNotNull()
        .isNotEmpty()
        .containsExactlyEntriesOf(
            new HashMap<>() {
              {
                put(START_EVENT, START_EVENT);
                put(SERVICE_TASK, SERVICE_TASK);
                put(END_EVENT, null);
              }
            });
  }
}
