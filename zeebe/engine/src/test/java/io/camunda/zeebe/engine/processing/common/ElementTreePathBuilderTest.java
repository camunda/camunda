/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder.ElementTreePathProperties;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Test;

public class ElementTreePathBuilderTest {
  private static final AtomicLong PROCESS_DEFINITION_KEY_SEQUENCER = new AtomicLong(1000);
  private Map<Long, ElementInstance> elementInstanceMap;

  @Before
  public void setUp() {
    elementInstanceMap = new HashMap<>();
  }

  @Test
  public void shouldBuildSimpleElementInstanceTreePath() {
    // given
    final var parentProcessInstanceRecord = createProcessInstanceRecord();
    final ElementInstance parentInstance =
        createElementInstanceForRecord(100, parentProcessInstanceRecord, "Activity");

    // when
    final ElementTreePathBuilder builder =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceMap::get)
            .withCallActivityIndexProvider(new CallActivityIdProvider(null))
            .withElementInstanceKey(parentInstance.getKey());

    final ElementTreePathProperties properties = builder.build();

    assertThat(properties.elementInstancePath()).isNotNull();
    assertThat(properties.elementInstancePath()).hasSize(1); // no call activities
    assertThat(properties.elementInstancePath().getFirst()).containsExactly(100L);
    assertThat(properties.processDefinitionPath()).hasSize(1);
    assertThat(properties.processDefinitionPath())
        .containsExactly(parentProcessInstanceRecord.getProcessDefinitionKey());
    assertThat(properties.callingElementPath()).isEmpty();
  }

  @Test
  public void shouldBuildMoreComplexElementInstanceTreePath() {
    // given
    final var parentProcessInstanceRecord = createProcessInstanceRecord();
    final ElementInstance parentInstance =
        createElementInstanceForRecord(100, parentProcessInstanceRecord, "Process");

    createElementInstanceForRecord(101, createProcessInstanceRecord(), "subProcess");

    final var subProcess2InstanceRecord = createProcessInstanceRecord();
    final ElementInstance subProcess2 =
        createElementInstanceWithParentForRecord(
            102, parentInstance, subProcess2InstanceRecord, "subProcess2");

    // when
    final ElementTreePathBuilder builder =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceMap::get)
            .withCallActivityIndexProvider(new CallActivityIdProvider(null))
            .withElementInstanceKey(subProcess2.getKey());

    final ElementTreePathProperties properties = builder.build();

    assertThat(properties.elementInstancePath()).isNotNull();
    assertThat(properties.elementInstancePath()).hasSize(1); // no call activities
    assertThat(properties.elementInstancePath().getFirst()).containsExactly(100L, 102L);
    assertThat(properties.processDefinitionPath()).hasSize(1);
    assertThat(properties.processDefinitionPath())
        .containsExactly(parentProcessInstanceRecord.getProcessDefinitionKey());
    assertThat(properties.callingElementPath()).isEmpty();
  }

  @Test
  public void shouldIncludeProcessDefinitionAndCallingElementTreePaths() {
    // given
    final var processARecord = createProcessInstanceRecord();
    final ElementInstance processAInstance =
        createElementInstanceForRecord(100, processARecord, "processA");

    final var callActivityRecord =
        createProcessInstanceRecord(processARecord.getProcessDefinitionKey());
    final ElementInstance callActivityElementInstance =
        createElementInstanceWithParentForRecord(
            101, processAInstance, callActivityRecord, "callActivity");

    final var processBRecord = createProcessInstanceRecord();
    processBRecord.setParentElementInstanceKey(callActivityElementInstance.getKey());
    final ElementInstance processB =
        createElementInstanceForRecord(102, processBRecord, "processB");

    final var subProcessCRecord = createProcessInstanceRecord();
    final ElementInstance subProcessC =
        createElementInstanceWithParentForRecord(103, processB, subProcessCRecord, "subProcessC");

    // when
    final ElementTreePathBuilder builder =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceMap::get)
            .withCallActivityIndexProvider(new CallActivityIdProvider(0))
            .withElementInstanceKey(subProcessC.getKey());

    final ElementTreePathProperties properties = builder.build();

    assertThat(properties.elementInstancePath()).isNotNull();
    assertThat(properties.elementInstancePath()).hasSize(2);
    assertThat(properties.elementInstancePath().getFirst()).containsExactly(100L, 101L);
    assertThat(properties.elementInstancePath().getLast()).containsExactly(102L, 103L);
    assertThat(properties.processDefinitionPath()).hasSize(2);
    assertThat(properties.processDefinitionPath())
        .containsExactly(
            processARecord.getProcessDefinitionKey(), processBRecord.getProcessDefinitionKey());
    assertThat(properties.callingElementPath()).hasSize(1);
    assertThat(properties.callingElementPath().getFirst()).isEqualTo(0);
  }

  private ElementInstance createElementInstanceForRecord(
      final long key, final ProcessInstanceRecord instanceRecord, final String elementId) {
    instanceRecord.setElementId(elementId);
    final ElementInstance callActivityElementInstance =
        new ElementInstance(key, ProcessInstanceIntent.ELEMENT_ACTIVATING, instanceRecord);
    elementInstanceMap.put(key, callActivityElementInstance);
    return callActivityElementInstance;
  }

  private ElementInstance createElementInstanceWithParentForRecord(
      final long key,
      final ElementInstance parent,
      final ProcessInstanceRecord instanceRecord,
      final String elementId) {
    instanceRecord.setElementId(elementId);
    final ElementInstance callActivityElementInstance =
        new ElementInstance(key, parent, ProcessInstanceIntent.ELEMENT_ACTIVATING, instanceRecord);
    elementInstanceMap.put(key, callActivityElementInstance);
    return callActivityElementInstance;
  }

  private ProcessInstanceRecord createProcessInstanceRecord() {
    return createProcessInstanceRecord(PROCESS_DEFINITION_KEY_SEQUENCER.incrementAndGet());
  }

  private ProcessInstanceRecord createProcessInstanceRecord(final long processDefinitionKey) {
    final ProcessInstanceRecord processInstanceRecord = new ProcessInstanceRecord();
    processInstanceRecord.setElementId("startEvent");
    processInstanceRecord.setBpmnProcessId(wrapString("process1"));
    processInstanceRecord.setProcessInstanceKey(1000L);
    processInstanceRecord.setFlowScopeKey(1001L);
    processInstanceRecord.setVersion(1);
    processInstanceRecord.setProcessDefinitionKey(processDefinitionKey);
    processInstanceRecord.setBpmnElementType(BpmnElementType.START_EVENT);

    return processInstanceRecord;
  }

  private record CallActivityIdProvider(Integer index) implements CallActivityIndexProvider {

    @Override
    public <T extends ExecutableFlowElement> T getFlowElement(
        final long processDefinitionKey,
        final String tenantId,
        final DirectBuffer elementId,
        final Class<T> elementType) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Integer getLexicographicIndex(
        final long processDefinitionKey, final String tenant, final DirectBuffer elementIdBuffer) {
      return index;
    }

    @Override
    public ExecutableCallActivity getCallActivityFlowElement(
        final long processDefinitionKey, final String tenantId, final DirectBuffer elementId) {
      throw new UnsupportedOperationException("not implemented");
    }
  }
}
