/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.junit.jupiter.api.Test;

final class LoopDetectionFilterTest {

  private static final String ELEMENT_ID = "element";
  private static final String FLOW_SCOPE_ID = "flowScope";

  @Test
  void shouldCountRegularElement() {
    // given a regular (non multi-instance-body) element
    final var value = record(BpmnElementType.SERVICE_TASK, ELEMENT_ID);

    // when / then
    assertThat(LoopDetectionFilter.shouldCount(value)).isTrue();
  }

  @Test
  void shouldCountMultiInstanceChild() {
    // given a child of a multi-instance body: its own type is not MULTI_INSTANCE_BODY
    final var value = record(BpmnElementType.SERVICE_TASK, ELEMENT_ID);

    // when / then: the children accumulate the activation count (parallel and sequential alike)
    assertThat(LoopDetectionFilter.shouldCount(value)).isTrue();
  }

  @Test
  void shouldNotCountMultiInstanceBody() {
    // given a multi-instance body: the container is never counted, its children are
    final var value = record(BpmnElementType.MULTI_INSTANCE_BODY, ELEMENT_ID);

    // when / then
    assertThat(LoopDetectionFilter.shouldCount(value)).isFalse();
  }

  @Test
  void shouldNotDetectMultiInstanceChildWithoutFlowScope() {
    assertThat(LoopDetectionFilter.isMultiInstanceChild(null)).isFalse();
  }

  @Test
  void shouldNotDetectMultiInstanceChildInsideRegularFlowScope() {
    final var flowScope = flowScope(BpmnElementType.SUB_PROCESS, FLOW_SCOPE_ID);

    assertThat(LoopDetectionFilter.isMultiInstanceChild(flowScope)).isFalse();
  }

  @Test
  void shouldDetectMultiInstanceChildInsideMultiInstanceBody() {
    final var flowScope = flowScope(BpmnElementType.MULTI_INSTANCE_BODY, FLOW_SCOPE_ID);

    assertThat(LoopDetectionFilter.isMultiInstanceChild(flowScope)).isTrue();
  }

  private static ProcessInstanceRecord record(final BpmnElementType elementType, final String id) {
    return new ProcessInstanceRecord().setBpmnElementType(elementType).setElementId(id);
  }

  private static ElementInstance flowScope(final BpmnElementType elementType, final String id) {
    return new ElementInstance(
        2L, ProcessInstanceIntent.ELEMENT_ACTIVATED, record(elementType, id));
  }
}
