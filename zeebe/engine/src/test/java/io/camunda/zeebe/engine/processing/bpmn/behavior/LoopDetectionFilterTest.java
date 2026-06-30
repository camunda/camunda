/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableLoopCharacteristics;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;

final class LoopDetectionFilterTest {

  private static final long PROCESS_DEFINITION_KEY = 1L;
  private static final String TENANT_ID = "tenant-1";
  private static final String ELEMENT_ID = "element";
  private static final String FLOW_SCOPE_ID = "flowScope";

  private final ProcessState processState = mock(ProcessState.class);

  @Test
  void shouldCountRegularElementWithoutFlowScope() {
    // given a regular element activated in the root process (no flow scope)
    final var value = record(BpmnElementType.SERVICE_TASK, ELEMENT_ID);

    // when / then
    assertThat(LoopDetectionFilter.shouldCount(processState, value, null)).isTrue();
  }

  @Test
  void shouldCountRegularElementWithRegularFlowScope() {
    // given a regular element activated inside a regular (non-MI) flow scope
    final var value = record(BpmnElementType.SERVICE_TASK, ELEMENT_ID);
    final var flowScope = flowScope(BpmnElementType.SUB_PROCESS, FLOW_SCOPE_ID);

    // when / then
    assertThat(LoopDetectionFilter.shouldCount(processState, value, flowScope)).isTrue();
  }

  @Test
  void shouldCountParallelMultiInstanceBody() {
    // given the activated element is a parallel multi-instance body
    final var value = record(BpmnElementType.MULTI_INSTANCE_BODY, ELEMENT_ID);
    mockMultiInstanceBody(value.getElementIdBuffer(), false);

    // when / then
    assertThat(LoopDetectionFilter.shouldCount(processState, value, null)).isTrue();
  }

  @Test
  void shouldNotCountSequentialMultiInstanceBody() {
    // given the activated element is a sequential multi-instance body
    final var value = record(BpmnElementType.MULTI_INSTANCE_BODY, ELEMENT_ID);
    mockMultiInstanceBody(value.getElementIdBuffer(), true);

    // when / then
    assertThat(LoopDetectionFilter.shouldCount(processState, value, null)).isFalse();
  }

  @Test
  void shouldCountSequentialMultiInstanceChild() {
    // given a child activated inside a sequential multi-instance body
    final var value = record(BpmnElementType.SERVICE_TASK, ELEMENT_ID);
    final var flowScope = flowScope(BpmnElementType.MULTI_INSTANCE_BODY, FLOW_SCOPE_ID);
    mockMultiInstanceBody(flowScope.getValue().getElementIdBuffer(), true);

    // when / then
    assertThat(LoopDetectionFilter.shouldCount(processState, value, flowScope)).isTrue();
  }

  @Test
  void shouldNotCountParallelMultiInstanceChild() {
    // given a child activated inside a parallel multi-instance body
    final var value = record(BpmnElementType.SERVICE_TASK, ELEMENT_ID);
    final var flowScope = flowScope(BpmnElementType.MULTI_INSTANCE_BODY, FLOW_SCOPE_ID);
    mockMultiInstanceBody(flowScope.getValue().getElementIdBuffer(), false);

    // when / then
    assertThat(LoopDetectionFilter.shouldCount(processState, value, flowScope)).isFalse();
  }

  @Test
  void shouldNotCountMultiInstanceBodyWhenFlowElementIsMissing() {
    // given the activated element is a multi-instance body that cannot be resolved
    final var value = record(BpmnElementType.MULTI_INSTANCE_BODY, ELEMENT_ID);
    when(processState.getFlowElement(
            eq(PROCESS_DEFINITION_KEY),
            eq(TENANT_ID),
            eq(value.getElementIdBuffer()),
            eq(ExecutableMultiInstanceBody.class)))
        .thenReturn(null);

    // when / then
    assertThat(LoopDetectionFilter.shouldCount(processState, value, null)).isFalse();
  }

  @Test
  void shouldNotCountChildWhenFlowScopeMultiInstanceBodyIsMissing() {
    // given a child whose multi-instance flow scope cannot be resolved
    final var value = record(BpmnElementType.SERVICE_TASK, ELEMENT_ID);
    final var flowScope = flowScope(BpmnElementType.MULTI_INSTANCE_BODY, FLOW_SCOPE_ID);
    when(processState.getFlowElement(
            eq(PROCESS_DEFINITION_KEY),
            eq(TENANT_ID),
            eq(flowScope.getValue().getElementIdBuffer()),
            eq(ExecutableMultiInstanceBody.class)))
        .thenReturn(null);

    // when / then
    assertThat(LoopDetectionFilter.shouldCount(processState, value, flowScope)).isFalse();
  }

  private static ProcessInstanceRecord record(final BpmnElementType elementType, final String id) {
    return new ProcessInstanceRecord()
        .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
        .setTenantId(TENANT_ID)
        .setBpmnElementType(elementType)
        .setElementId(id);
  }

  private static ElementInstance flowScope(final BpmnElementType elementType, final String id) {
    return new ElementInstance(
        2L, ProcessInstanceIntent.ELEMENT_ACTIVATED, record(elementType, id));
  }

  private void mockMultiInstanceBody(final DirectBuffer elementId, final boolean sequential) {
    final var loopCharacteristics = mock(ExecutableLoopCharacteristics.class);
    when(loopCharacteristics.isSequential()).thenReturn(sequential);
    final var miBody = mock(ExecutableMultiInstanceBody.class);
    when(miBody.getLoopCharacteristics()).thenReturn(loopCharacteristics);
    when(processState.getFlowElement(
            eq(PROCESS_DEFINITION_KEY),
            eq(TENANT_ID),
            eq(elementId),
            eq(ExecutableMultiInstanceBody.class)))
        .thenReturn(miBody);
  }
}
