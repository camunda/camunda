/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRuntimeInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs the element instance state contract against the RocksDB state, plus the tests for the parts
 * of the state that only the RocksDB state supports so far.
 */
@ExtendWith(ProcessingStateExtension.class)
public final class ElementInstanceStateTest extends AbstractElementInstanceStateContractTest {

  private static final long PROCESS_KEY = 123;

  private MutableProcessingState processingState;

  @Override
  protected MutableElementInstanceState createElementInstanceState() {
    return processingState.getElementInstanceState();
  }

  @Override
  protected VariableState variableState() {
    return processingState.getVariableState();
  }

  @Test
  public void shouldInsertProcessInstanceKeyByProcessDefinitionKey() {
    // given
    final var processInstanceRecord =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    final var processInstanceKey = 100L;
    elementInstanceState.newInstance(
        processInstanceKey, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final List<Long> processInstanceKeys =
        elementInstanceState.getProcessInstanceKeysByDefinitionKey(
            processInstanceRecord.getProcessDefinitionKey());

    // then
    Assertions.assertThat(processInstanceKeys).hasSize(1);
    assertThat(processInstanceKeys).containsOnly(processInstanceKey);
  }

  @Test
  public void shouldInsertMultipleProcessInstanceKeyByProcessDefinitionKey() {
    // given
    final var processDefinitionKey = 100L;
    final var processInstanceRecord1 =
        createProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setProcessDefinitionKey(processDefinitionKey);
    final var processInstanceRecord2 =
        createProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setProcessDefinitionKey(processDefinitionKey);
    final var processInstanceKey1 = 101L;
    final var processInstanceKey2 = 102L;
    elementInstanceState.newInstance(
        processInstanceKey1, processInstanceRecord1, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        processInstanceKey2, processInstanceRecord2, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final List<Long> processInstanceKeys =
        elementInstanceState.getProcessInstanceKeysByDefinitionKey(processDefinitionKey);

    // then
    Assertions.assertThat(processInstanceKeys).hasSize(2);
    assertThat(processInstanceKeys).containsOnly(processInstanceKey1, processInstanceKey2);
  }

  @Test
  public void shouldOnlyReturnProcessInstanceKeyBelongingToProcessDefinition() {
    // given
    final var processDefinitionKey = 100L;
    final var processInstanceRecord1 =
        createProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setProcessDefinitionKey(processDefinitionKey);
    final var processInstanceRecord2 =
        createProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setProcessDefinitionKey(101L);
    final var processInstanceKey1 = 102L;
    final var processInstanceKey2 = 103L;
    elementInstanceState.newInstance(
        processInstanceKey1, processInstanceRecord1, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        processInstanceKey2, processInstanceRecord2, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final List<Long> processInstanceKeys =
        elementInstanceState.getProcessInstanceKeysByDefinitionKey(processDefinitionKey);

    // then
    Assertions.assertThat(processInstanceKeys).hasSize(1);
    assertThat(processInstanceKeys).containsOnly(processInstanceKey1);
  }

  @Test
  public void shouldOnlyReturnEmptyListWhenNoProcessInstanceForProcessDefinitionKeyExist() {
    // given
    final var processDefinitionKey = 100L;
    final var processInstanceRecord =
        createProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setProcessDefinitionKey(101L);
    final var processInstanceKey = 102L;
    elementInstanceState.newInstance(
        processInstanceKey, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final List<Long> processInstanceKeys =
        elementInstanceState.getProcessInstanceKeysByDefinitionKey(processDefinitionKey);

    // then
    Assertions.assertThat(processInstanceKeys).isEmpty();
  }

  @Test
  public void shouldRemoveProcessInstanceByProcessDefinitionKeyOnRemoval() {
    // given
    final var processDefinitionKey = 100L;
    final var processInstanceRecord =
        createProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setProcessDefinitionKey(processDefinitionKey);
    final var processInstanceKey = 101L;
    elementInstanceState.newInstance(
        processInstanceKey, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    elementInstanceState.removeInstance(processInstanceKey);

    // then
    final List<Long> processInstanceKeys =
        elementInstanceState.getProcessInstanceKeysByDefinitionKey(processDefinitionKey);
    Assertions.assertThat(processInstanceKeys).isEmpty();
    final var hasRunningInstances =
        elementInstanceState.hasActiveProcessInstances(
            processInstanceRecord.getProcessDefinitionKey(), Collections.emptyList());
    assertThat(hasRunningInstances).isFalse();
  }

  @Test
  public void shouldNotLeakMemoryOnRemoval() {
    // given
    final int parent = 100;
    final int child = 101;

    final ProcessInstanceRecord processInstanceRecord =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            parent, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    processingState
        .getVariableState()
        .setVariableLocal(
            1, parent, PROCESS_KEY, BufferUtil.wrapString("a"), MsgPackUtil.asMsgPack("1"));

    processInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, child, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    processingState
        .getVariableState()
        .setVariableLocal(
            2, child, PROCESS_KEY, BufferUtil.wrapString("b"), MsgPackUtil.asMsgPack("2"));

    // when
    elementInstanceState.removeInstance(101);
    elementInstanceState.removeInstance(100);

    // then
    final var nonEmptyColumns =
        Arrays.stream(ZbColumnFamilies.values())
            .filter(not(ZbColumnFamilies.KEY::equals))
            // ACTIVE_PROCESS_INSTANCE_COUNT tracks the count of active process instances
            .filter(not(ZbColumnFamilies.ACTIVE_PROCESS_INSTANCE_COUNT::equals))
            .filter(not(processingState::isEmpty))
            .collect(Collectors.toList());

    assertThat(nonEmptyColumns).describedAs("Expected all columns to be empty").isEmpty();
  }

  @Test
  public void shouldFindRunningInstancesForProcessDefinitionKey() {
    // given
    final var processInstanceRecord =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    final var processInstanceKey = 100L;
    elementInstanceState.newInstance(
        processInstanceKey, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final var hasRunningInstances =
        elementInstanceState.hasActiveProcessInstances(
            processInstanceRecord.getProcessDefinitionKey(), Collections.emptyList());

    // then
    Assertions.assertThat(hasRunningInstances).isTrue();
  }

  @Test
  public void shouldNotFindRunningInstancesForProcessDefinitionKey() {
    // given
    final var processInstanceRecord =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);

    // when
    final var hasRunningInstances =
        elementInstanceState.hasActiveProcessInstances(
            processInstanceRecord.getProcessDefinitionKey(), Collections.emptyList());

    // then
    Assertions.assertThat(hasRunningInstances).isFalse();
  }

  @Test
  public void shouldNotFindRunningInstanceForBannedInstance() {
    // given
    final var processInstanceKey = 123L;
    final var processInstanceRecord =
        createProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setProcessInstanceKey(processInstanceKey);

    // when
    final var hasRunningInstances =
        elementInstanceState.hasActiveProcessInstances(
            processInstanceRecord.getProcessDefinitionKey(),
            Collections.singletonList(processInstanceKey));

    // then
    Assertions.assertThat(hasRunningInstances).isFalse();
  }

  @Test
  public void shouldUpdateAwaitResultMetadata() {
    final long key = 10L;
    final int streamId = 2;
    final long requestId = 10000L;

    // when
    elementInstanceState.setAwaitResultRequestMetadata(
        key,
        new AwaitProcessInstanceResultMetadata()
            .setRequestStreamId(streamId)
            .setRequestId(requestId));
    final AwaitProcessInstanceResultMetadata metadata =
        elementInstanceState.getAwaitResultRequestMetadata(key);

    // then
    assertThat(metadata.getRequestId()).isEqualTo(requestId);
    assertThat(metadata.getRequestStreamId()).isEqualTo(streamId);
  }

  @Test
  public void shouldReturnZeroWhenNoProcessInstances() {
    // when
    long count = elementInstanceState.getActiveProcessInstanceCount();
    // then
    assertThat(count).isEqualTo(0);

    // when we create a PI
    final ProcessInstanceRecord processInstanceRecord =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    elementInstanceState.newInstance(
        200, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // then we should return one
    count = elementInstanceState.getActiveProcessInstanceCount();
    assertThat(count).isEqualTo(1);
  }

  @Test
  public void shouldCountMultipleProcessInstances() {
    // given
    final ProcessInstanceRecord processInstanceRecord1 =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    final ProcessInstanceRecord processInstanceRecord2 =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);

    elementInstanceState.newInstance(
        201, processInstanceRecord1, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        202, processInstanceRecord2, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    // when
    final long count = elementInstanceState.getActiveProcessInstanceCount();
    // then
    assertThat(count).isEqualTo(2);
  }

  @Test
  public void shouldDecreaseCountWhenRootProcessInstanceRemoved() {
    // given
    final ProcessInstanceRecord processInstanceRecord1 =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    final ProcessInstanceRecord processInstanceRecord2 =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    elementInstanceState.newInstance(
        203, processInstanceRecord1, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        204, processInstanceRecord2, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    // when
    elementInstanceState.removeInstance(203);
    final long count = elementInstanceState.getActiveProcessInstanceCount();
    // then the count should be one
    assertThat(count).isEqualTo(1);
  }

  @Test
  public void shouldCountChildInstancesProcessInstances() {
    // given
    final ProcessInstanceRecord rootRecord =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    final long rootKey1 = 300L;
    final long rootKey2 = 301L;
    final ElementInstance rootInstance1 =
        elementInstanceState.newInstance(
            rootKey1, rootRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    final ElementInstance rootInstance2 =
        elementInstanceState.newInstance(
            rootKey2, rootRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // add child process instances started via call activities
    final ProcessInstanceRecord childProcessRecord =
        createProcessInstanceRecord()
            .setElementId("childProcess")
            .setBpmnElementType(BpmnElementType.PROCESS);
    elementInstanceState.newInstance(
        rootInstance1, 500L, childProcessRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    elementInstanceState.newInstance(
        rootInstance2, 501L, childProcessRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    // when
    final long count = elementInstanceState.getActiveProcessInstanceCount();

    // then we should have 2 root process instances + 2 child process instances
    assertThat(count).isEqualTo(4);
  }

  @Test
  public void shouldRemoveRuntimeInstructionsForProcessInstance() {
    // given
    final long processInstanceKey = 100L;
    final var instruction =
        new ProcessInstanceCreationRuntimeInstruction()
            .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
            .setAfterElementId("taskA");
    elementInstanceState.addRuntimeInstructions(processInstanceKey, List.of(instruction));

    // verify instructions exist
    assertThat(elementInstanceState.getRuntimeInstructionsForElementId(processInstanceKey, "taskA"))
        .isNotEmpty();

    // when
    elementInstanceState.removeRuntimeInstructions(processInstanceKey);

    // then
    assertThat(elementInstanceState.getRuntimeInstructionsForElementId(processInstanceKey, "taskA"))
        .isEmpty();
  }

  @Test
  public void shouldNotFailWhenRemovingNonExistentRuntimeInstructions() {
    // given - no runtime instructions exist for this key
    final long processInstanceKey = 999L;

    // when/then - should not throw
    elementInstanceState.removeRuntimeInstructions(processInstanceKey);
  }
}
