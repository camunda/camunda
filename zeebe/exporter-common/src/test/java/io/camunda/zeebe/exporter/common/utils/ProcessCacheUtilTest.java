/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ProcessCacheUtilTest {

  @Test
  void shouldExtractCallActivityIds() {
    // given
    final String processId = "testProcessId";
    final var model = buildModel(processId, List.of("C_Activity", "A_Activity", "D_Activity"));
    final var bpmnXml = Bpmn.convertToString(model);
    // when
    final var callActivities =
        ProcessCacheUtil.extractProcessDiagramData(bpmnXml, processId).callActivityIds();
    // then
    assertThat(callActivities).containsExactly("A_Activity", "C_Activity", "D_Activity");
  }

  @Test
  void shouldSortCallActivityIds() {
    // given
    final var model =
        buildModel("testProcessId", List.of("C_Activity", "A_Activity", "D_Activity"));
    final var callActivities = model.getModelElementsByType(CallActivity.class).stream().toList();
    // when
    final var ids = ProcessCacheUtil.sortedCallActivityIds(callActivities);
    // then
    assertThat(ids).containsExactly("A_Activity", "C_Activity", "D_Activity");
  }

  private BpmnModelInstance buildModel(final String processId, final List<String> callActivities) {
    final var builder = Bpmn.createExecutableProcess(processId).startEvent();
    callActivities.forEach(ca -> builder.callActivity(ca).zeebeProcessId(ca));
    return builder.done();
  }
}
