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
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ElementTreePathBuilderTest {
  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableElementInstanceState elementInstanceState;
  private MutableProcessingState processingState;

  @Before
  public void setUp() {
    processingState = stateRule.getProcessingState();
    elementInstanceState = processingState.getElementInstanceState();
  }

  @Test
  public void shouldBuildElementInstanceTreePath() {
    // given
    final var parentProcessInstanceRecord = createProcessInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, parentProcessInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final var subProcessInstanceRecord = createProcessInstanceRecord();
    subProcessInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, subProcessInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    final var subProcess2InstanceRecord = createProcessInstanceRecord();
    subProcess2InstanceRecord.setElementId("subProcess2");
    final ElementInstance subProcess2 =
        elementInstanceState.newInstance(
            parentInstance,
            102,
            subProcess2InstanceRecord,
            ProcessInstanceIntent.ELEMENT_ACTIVATING);

    // when
    final ElementTreePathBuilder builder =
        new ElementTreePathBuilder()
            .withElementInstanceState(elementInstanceState)
            .withElementInstanceKey(subProcess2.getKey());

    final ElementTreePathProperties properties = builder.build();

    assertThat(properties.elementInstancePath()).isNotNull();
    assertThat(properties.elementInstancePath()).hasSize(1); // no call activities
    assertThat(properties.elementInstancePath().getFirst()).containsExactly(100L, 102L);
    assertThat(properties.processDefinitionPath()).hasSize(1);
    assertThat(properties.processDefinitionPath())
        .containsExactly(parentProcessInstanceRecord.getProcessDefinitionKey());
  }

  @Test
  public void shouldIncludeProcessDefinitionAndCallingElementTreePaths() {
    // given
    final var processARecord = createProcessInstanceRecord();
    final ElementInstance processA =
        elementInstanceState.newInstance(
            100, processARecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final var callActivityRecord = createProcessInstanceRecord();
    callActivityRecord.setElementId("callActivity");
    final ElementInstance callActivityElementInstance =
        elementInstanceState.newInstance(
            processA, 101, callActivityRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    final var processBRecord = createProcessInstanceRecord();
    processBRecord.setElementId("processB");
    processBRecord.setParentElementInstanceKey(callActivityElementInstance.getKey());
    final ElementInstance processB =
        elementInstanceState.newInstance(
            102, processBRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    final var subProcessCRecord = createProcessInstanceRecord();
    callActivityRecord.setElementId("subProcessC");
    final ElementInstance subProcessC =
        elementInstanceState.newInstance(
            processB, 103, subProcessCRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    // when
    final ElementTreePathBuilder builder =
        new ElementTreePathBuilder()
            .withElementInstanceState(elementInstanceState)
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
    assertThat(properties.callingElementPath().getFirst()).isEqualTo("callActivity");
  }

  private ProcessInstanceRecord createProcessInstanceRecord() {
    final ProcessInstanceRecord processInstanceRecord = new ProcessInstanceRecord();
    processInstanceRecord.setElementId("startEvent");
    processInstanceRecord.setBpmnProcessId(wrapString("process1"));
    processInstanceRecord.setProcessInstanceKey(1000L);
    processInstanceRecord.setFlowScopeKey(1001L);
    processInstanceRecord.setVersion(1);
    processInstanceRecord.setProcessDefinitionKey(ThreadLocalRandom.current().nextLong(1000));
    processInstanceRecord.setBpmnElementType(BpmnElementType.START_EVENT);

    return processInstanceRecord;
  }
}
