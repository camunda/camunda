/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRuntimeInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.agrona.DirectBuffer;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every {@link MutableElementInstanceState} implementation must share. Each backend runs
 * these tests by extending this class.
 */
public abstract class AbstractElementInstanceStateContractTest {

  protected static final String TENANT_ID = "tenant";
  private static final long PROCESS_KEY = 123;
  private static final long FLOW_SCOPE_KEY = 1L;
  private static final long OTHER_FLOW_SCOPE_KEY = 2L;
  private static final DirectBuffer GATEWAY_ELEMENT_ID = wrapString("gateway-1");
  private static final DirectBuffer OTHER_GATEWAY_ELEMENT_ID = wrapString("gateway-2");
  private static final DirectBuffer SEQUENCE_FLOW_ELEMENT_ID = wrapString("flow-1");
  private static final DirectBuffer OTHER_SEQUENCE_FLOW_ELEMENT_ID = wrapString("flow-2");

  protected MutableElementInstanceState elementInstanceState;

  /** Creates the state under test. Called before each test. */
  protected abstract MutableElementInstanceState createElementInstanceState();

  /** The variable state the element instance state under test creates and removes scopes in. */
  protected abstract MutableVariableState variableState();

  /**
   * Asserts that the backend holds no data anymore, except for data that is expected to stay (like
   * a counter). Called after all instances were removed.
   */
  protected abstract void assertNoStateLeft();

  @BeforeEach
  void setUpElementInstanceState() {
    elementInstanceState = createElementInstanceState();
  }

  @Test
  void shouldCreateNewInstance() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();

    // when
    final ElementInstance elementInstance =
        elementInstanceState.newInstance(
            100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // then
    assertElementInstance(elementInstance, 0);
  }

  @Test
  void shouldCreateNewInstanceWithParent() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final ProcessInstanceRecord otherRecord = createProcessInstanceRecord();
    otherRecord.setElementId("subProcess");
    final ElementInstance childInstance =
        elementInstanceState.newInstance(
            parentInstance, 101, otherRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    // then
    assertElementInstance(parentInstance, 1);
    assertChildInstance(childInstance, 101, "subProcess");
  }

  @Test
  void shouldFindElementInstance() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();
    elementInstanceState.newInstance(
        100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final ElementInstance instance = elementInstanceState.getInstance(100);

    // then
    assertElementInstance(instance, 0);
  }

  @Test
  void shouldNotLookUpElementInstanceForNegativeKey() {
    // given - a row stored under -1, which a real lookup would find
    elementInstanceState.newInstance(
        -1, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final ElementInstance instance = elementInstanceState.getInstance(-1L);

    // then - null despite the stored row means getInstance skipped the lookup
    assertThat(instance).isNull();
  }

  @Test
  void shouldFindChildInstance() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    processInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    // when
    final ElementInstance childInstance = elementInstanceState.getInstance(101L);

    // then
    assertChildInstance(childInstance, 101, "subProcess");
  }

  @Test
  void shouldFindParentInstance() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    processInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    // when
    final ElementInstance updatedParentInstance = elementInstanceState.getInstance(100L);

    // then
    assertElementInstance(updatedParentInstance, 1);
  }

  @Test
  void shouldRemoveParentInstanceAfterRemovingChild() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    processInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    elementInstanceState.removeInstance(101L);

    // when
    elementInstanceState.removeInstance(100L);

    // then
    assertThat(elementInstanceState.getInstance(101L)).isNull();
    assertThat(elementInstanceState.getInstance(100L)).isNull();
    assertThat(elementInstanceState.getChildren(100L)).isEmpty();
  }

  @Test
  void shouldRemoveChildInstance() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    processInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    processInstanceRecord.setElementId("subProcess2");
    elementInstanceState.newInstance(
        parentInstance, 102, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    // when
    elementInstanceState.removeInstance(101L);

    // then
    assertThat(elementInstanceState.getInstance(101L)).isNull();
    assertThat(elementInstanceState.getChildren(100L)).hasSize(1);
    assertChildInstance(elementInstanceState.getInstance(102L), 102L, "subProcess2");
    assertElementInstance(elementInstanceState.getInstance(100L), 1);
  }

  @Test
  void shouldIgnoreRemovalOfMissingInstance() {
    // given
    elementInstanceState.newInstance(
        100, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    elementInstanceState.removeInstance(999L);

    // then
    assertElementInstance(elementInstanceState.getInstance(100L), 0);
  }

  @Test
  void shouldUpdateElementInstance() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    instance.setState(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    instance.setJobKey(5);
    elementInstanceState.updateInstance(instance);

    // then
    final ElementInstance updatedInstance = elementInstanceState.getInstance(100);

    assertThat(updatedInstance.getKey()).isEqualTo(100);
    assertThat(updatedInstance.getState()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(updatedInstance.getJobKey()).isEqualTo(5);
    assertThat(updatedInstance.canTerminate()).isTrue();
    assertThat(updatedInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    assertProcessInstanceRecord(updatedInstance.getValue());
  }

  @Test
  void shouldUpdateElementInstanceWithModifier() {
    // given
    elementInstanceState.newInstance(
        100, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    elementInstanceState.updateInstance(
        100L,
        instance -> {
          instance.setJobKey(7);
          instance.incrementMultiInstanceLoopCounter();
        });

    // then
    final ElementInstance updatedInstance = elementInstanceState.getInstance(100);
    assertThat(updatedInstance.getJobKey()).isEqualTo(7);
    assertThat(updatedInstance.getMultiInstanceLoopCounter()).isEqualTo(1);
    assertProcessInstanceRecord(updatedInstance.getValue());
  }

  @Test
  void shouldNotUpdateElementInstance() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    instance.setState(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    instance.setJobKey(5);

    // then
    final ElementInstance updatedInstance = elementInstanceState.getInstance(100);

    assertThat(updatedInstance.getKey()).isEqualTo(100);
    assertThat(updatedInstance.getState()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    assertThat(updatedInstance.getJobKey()).isEqualTo(0L);
    assertThat(updatedInstance.canTerminate()).isTrue();
    assertThat(updatedInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    assertProcessInstanceRecord(updatedInstance.getValue());
  }

  @Test
  void shouldNotUpdateElementInstanceWithoutFlush() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    instance.setState(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    instance.setJobKey(5);

    // then
    assertElementInstance(elementInstanceState.getInstance(100), 0);
  }

  @Test
  void shouldCollectChildInstances() {
    // given
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    processInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    processInstanceRecord.setElementId("subProcess2");
    elementInstanceState.newInstance(
        parentInstance, 102, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    // when
    final List<ElementInstance> children = elementInstanceState.getChildren(100L);

    // then
    assertThat(children).hasSize(2);
    assertChildInstance(children.get(0), 101, "subProcess");
    assertChildInstance(children.get(1), 102, "subProcess2");
  }

  @Test
  void shouldNotCollectChildrenOfMissingParent() {
    // when
    final List<ElementInstance> children = elementInstanceState.getChildren(100L);

    // then
    assertThat(children).isEmpty();
  }

  @Test
  void shouldVisitChildrenInKeyOrderStartingAtKey() {
    // given
    final var parentInstance = createParentWithChildren(100, 103, 101, 102);

    // when
    final List<Long> visited = new ArrayList<>();
    elementInstanceState.forEachChild(
        parentInstance.getKey(),
        102L,
        (key, child) -> {
          assertThat(child.getKey()).isEqualTo(key);
          visited.add(key);
          return true;
        });

    // then
    assertThat(visited).containsExactly(102L, 103L);
  }

  @Test
  void shouldVisitAllChildrenWhenStartingAtNoKey() {
    // given
    final var parentInstance = createParentWithChildren(100, 101, 102);

    // when
    final List<Long> visited = new ArrayList<>();
    elementInstanceState.forEachChild(
        parentInstance.getKey(),
        -1L,
        (key, child) -> {
          visited.add(key);
          return true;
        });

    // then
    assertThat(visited).containsExactly(101L, 102L);
  }

  @Test
  void shouldStopVisitingChildrenWhenVisitorReturnsFalse() {
    // given
    final var parentInstance = createParentWithChildren(100, 101, 102, 103);

    // when
    final List<Long> visited = new ArrayList<>();
    elementInstanceState.forEachChild(
        parentInstance.getKey(),
        -1L,
        (key, child) -> {
          visited.add(key);
          return visited.size() < 2;
        });

    // then
    assertThat(visited).containsExactly(101L, 102L);
  }

  @Test
  void shouldVisitOnlyChildrenMatchingFilter() {
    // given
    final var parentInstance = createParentWithChildren(100, 101, 102, 103);
    elementInstanceState.updateInstance(102L, child -> child.setJobKey(42));

    // when
    final List<Long> visited = new ArrayList<>();
    elementInstanceState.forEachChild(
        parentInstance.getKey(),
        -1L,
        (key, child) -> {
          visited.add(key);
          return true;
        },
        child -> child.getJobKey() == 42);

    // then
    assertThat(visited).containsExactly(102L);
  }

  @Test
  void shouldVisitChildKeysUntilVisitorReturnsFalse() {
    // given
    final var parentInstance = createParentWithChildren(100, 101, 102, 103);

    // when
    final List<Long> visited = new ArrayList<>();
    elementInstanceState.forEachChildKey(
        parentInstance.getKey(),
        key -> {
          visited.add(key);
          return key < 102L;
        });

    // then
    assertThat(visited).containsExactly(101L, 102L);
  }

  @Test
  void shouldCreateAndRemoveVariableScopes() {
    // given
    final var parentInstance = createParentWithChildren(100, 101);

    // when
    final long parentScopeOfChild = variableState().getParentScopeKey(101L);
    final long parentScopeOfParent = variableState().getParentScopeKey(parentInstance.getKey());
    elementInstanceState.removeInstance(101L);

    // then
    assertThat(parentScopeOfChild).isEqualTo(100L);
    assertThat(parentScopeOfParent).isEqualTo(VariableState.NO_PARENT);
    assertThat(variableState().getParentScopeKey(101L)).isEqualTo(VariableState.NO_PARENT);
  }

  @Test
  void shouldInsertProcessInstanceKeyByProcessDefinitionKey() {
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
    assertThat(processInstanceKeys).containsExactly(processInstanceKey);
  }

  @Test
  void shouldInsertMultipleProcessInstanceKeyByProcessDefinitionKey() {
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
    assertThat(processInstanceKeys).containsExactly(processInstanceKey1, processInstanceKey2);
  }

  @Test
  void shouldOnlyReturnProcessInstanceKeyBelongingToProcessDefinition() {
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
    assertThat(processInstanceKeys).containsExactly(processInstanceKey1);
  }

  @Test
  void shouldOnlyReturnEmptyListWhenNoProcessInstanceForProcessDefinitionKeyExist() {
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
    assertThat(processInstanceKeys).isEmpty();
  }

  @Test
  void shouldNotReturnNonProcessElementInstancesByProcessDefinitionKey() {
    // given - a process instance and one of its elements, both of definition 2
    createParentWithChildren(100, 101);

    // when
    final List<Long> processInstanceKeys =
        elementInstanceState.getProcessInstanceKeysByDefinitionKey(2);

    // then - the parent is a start event as well, so nothing is a process instance
    assertThat(processInstanceKeys).isEmpty();
    assertThat(elementInstanceState.getActiveProcessInstanceCount()).isZero();
  }

  @Test
  void shouldRemoveProcessInstanceByProcessDefinitionKeyOnRemoval() {
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
    assertThat(elementInstanceState.getProcessInstanceKeysByDefinitionKey(processDefinitionKey))
        .isEmpty();
    assertThat(
            elementInstanceState.hasActiveProcessInstances(
                processDefinitionKey, Collections.emptyList()))
        .isFalse();
  }

  @Test
  void shouldMoveProcessInstanceToTargetDefinitionOnMigration() {
    // given
    final var sourceDefinitionKey = 100L;
    final var targetDefinitionKey = 200L;
    final var processInstanceKey = 101L;
    elementInstanceState.newInstance(
        processInstanceKey,
        createProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setProcessDefinitionKey(sourceDefinitionKey),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when - same calls as the ProcessInstanceElementMigrated appliers
    elementInstanceState.updateInstance(
        processInstanceKey,
        instance -> instance.getValue().setProcessDefinitionKey(targetDefinitionKey));
    elementInstanceState.deleteProcessInstanceKeyByDefinitionKey(
        processInstanceKey, sourceDefinitionKey);
    elementInstanceState.insertProcessInstanceKeyByDefinitionKey(
        processInstanceKey, targetDefinitionKey);

    // then
    assertThat(elementInstanceState.getProcessInstanceKeysByDefinitionKey(sourceDefinitionKey))
        .isEmpty();
    assertThat(elementInstanceState.getProcessInstanceKeysByDefinitionKey(targetDefinitionKey))
        .containsExactly(processInstanceKey);
    assertThat(elementInstanceState.getActiveProcessInstanceCount()).isOne();

    // when - removed after the migration
    elementInstanceState.removeInstance(processInstanceKey);

    // then
    assertThat(elementInstanceState.getProcessInstanceKeysByDefinitionKey(targetDefinitionKey))
        .isEmpty();
    assertThat(elementInstanceState.getActiveProcessInstanceCount()).isZero();
  }

  @Test
  void shouldNotLeakMemoryOnRemoval() {
    // given
    final int parent = 100;
    final int child = 101;

    final ProcessInstanceRecord processInstanceRecord =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            parent, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    variableState()
        .setVariableLocal(1, parent, PROCESS_KEY, wrapString("a"), MsgPackUtil.asMsgPack("1"));
    elementInstanceState.setAwaitResultRequestMetadata(
        parent,
        TENANT_ID,
        new AwaitProcessInstanceResultMetadata().setRequestId(1).setRequestStreamId(2));

    processInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, child, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    variableState()
        .setVariableLocal(2, child, PROCESS_KEY, wrapString("b"), MsgPackUtil.asMsgPack("2"));
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        child, wrapString("gateway"), wrapString("flow"));

    // when
    elementInstanceState.removeInstance(101);
    elementInstanceState.removeInstance(100);

    // then
    assertNoStateLeft();
  }

  @Test
  void shouldFindRunningInstancesForProcessDefinitionKey() {
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
    assertThat(hasRunningInstances).isTrue();
  }

  @Test
  void shouldNotFindRunningInstancesForProcessDefinitionKey() {
    // given
    final var processInstanceRecord =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);

    // when
    final var hasRunningInstances =
        elementInstanceState.hasActiveProcessInstances(
            processInstanceRecord.getProcessDefinitionKey(), Collections.emptyList());

    // then
    assertThat(hasRunningInstances).isFalse();
  }

  @Test
  void shouldNotFindRunningInstanceForBannedInstance() {
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
    assertThat(hasRunningInstances).isFalse();
  }

  @Test
  void shouldIgnoreBannedInstancesWhenFindingRunningInstances() {
    // given
    final var processInstanceRecord =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    elementInstanceState.newInstance(
        100L, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        101L, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    final var definitionKey = processInstanceRecord.getProcessDefinitionKey();

    // when / then
    assertThat(elementInstanceState.hasActiveProcessInstances(definitionKey, List.of(100L)))
        .isTrue();
    assertThat(elementInstanceState.hasActiveProcessInstances(definitionKey, List.of(100L, 101L)))
        .isFalse();
  }

  @Test
  void shouldUpdateAwaitResultMetadata() {
    // given
    final long key = 10L;
    final int streamId = 2;
    final long requestId = 10000L;

    // when
    elementInstanceState.setAwaitResultRequestMetadata(
        key,
        TENANT_ID,
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
  void shouldRemoveAwaitResultMetadataWithInstance() {
    // given - only a process instance has await result metadata
    final long key = 100L;
    elementInstanceState.newInstance(
        key,
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.setAwaitResultRequestMetadata(
        key,
        TENANT_ID,
        new AwaitProcessInstanceResultMetadata().setRequestId(1).setRequestStreamId(2));

    // when
    elementInstanceState.removeInstance(key);

    // then
    assertThat(elementInstanceState.getAwaitResultRequestMetadata(key)).isNull();
  }

  @Test
  void shouldReturnZeroWhenNoProcessInstances() {
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
  void shouldCountMultipleProcessInstances() {
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
  void shouldDecreaseCountWhenRootProcessInstanceRemoved() {
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
  void shouldCountChildInstancesProcessInstances() {
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
  void shouldReturnRuntimeInstructionsForElementId() {
    // given
    final long processInstanceKey = 100L;
    final var terminateAfterA =
        new ProcessInstanceCreationRuntimeInstruction()
            .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
            .setAfterElementId("taskA");
    final var terminateAfterB =
        new ProcessInstanceCreationRuntimeInstruction()
            .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
            .setAfterElementId("taskB");

    // when
    elementInstanceState.addRuntimeInstructions(
        processInstanceKey, TENANT_ID, List.of(terminateAfterA, terminateAfterB));

    // then
    assertThat(elementInstanceState.getRuntimeInstructionsForElementId(processInstanceKey, "taskA"))
        .extracting(RuntimeInstructionValue::getAfterElementId, RuntimeInstructionValue::getType)
        .containsExactly(tuple("taskA", RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE));
    assertThat(elementInstanceState.getRuntimeInstructionsForElementId(processInstanceKey, "taskC"))
        .isEmpty();
    assertThat(elementInstanceState.getRuntimeInstructionsForElementId(999L, "taskA")).isEmpty();
  }

  @Test
  void shouldRemoveRuntimeInstructionsForProcessInstance() {
    // given
    final long processInstanceKey = 100L;
    final var instruction =
        new ProcessInstanceCreationRuntimeInstruction()
            .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
            .setAfterElementId("taskA");
    elementInstanceState.addRuntimeInstructions(
        processInstanceKey, TENANT_ID, List.of(instruction));

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
  void shouldNotFailWhenRemovingNonExistentRuntimeInstructions() {
    // given - no runtime instructions exist for this key
    final long processInstanceKey = 999L;

    // when/then - should not throw
    elementInstanceState.removeRuntimeInstructions(processInstanceKey);
  }

  @Test
  void shouldFindActiveProcessInstanceByBusinessId() {
    // given
    createRootProcessInstanceWithBusinessId(100L, "order-1", "process", "tenant");
    createRootProcessInstanceWithBusinessId(101L, "order-1", "process", "tenant");

    // when / then
    assertThat(
            elementInstanceState.hasActiveProcessInstanceWithBusinessId(
                "order-1", "process", "tenant", key -> false))
        .isTrue();
    assertThat(
            elementInstanceState.hasActiveProcessInstanceWithBusinessId(
                "order-1", "process", "tenant", key -> key == 100L))
        .isTrue();
    assertThat(
            elementInstanceState.hasActiveProcessInstanceWithBusinessId(
                "order-1", "process", "tenant", key -> key == 100L || key == 101L))
        .isFalse();
  }

  @Test
  void shouldNotFindProcessInstanceOfOtherBusinessIdProcessOrTenant() {
    // given
    createRootProcessInstanceWithBusinessId(100L, "order-1", "process", "tenant");

    // when / then
    assertThat(
            elementInstanceState.hasActiveProcessInstanceWithBusinessId(
                "order-2", "process", "tenant", key -> false))
        .isFalse();
    assertThat(
            elementInstanceState.hasActiveProcessInstanceWithBusinessId(
                "order-1", "other-process", "tenant", key -> false))
        .isFalse();
    assertThat(
            elementInstanceState.hasActiveProcessInstanceWithBusinessId(
                "order-1", "process", "other-tenant", key -> false))
        .isFalse();
  }

  @Test
  void shouldNotFindProcessInstanceByBusinessIdAfterDeletion() {
    // given
    createRootProcessInstanceWithBusinessId(100L, "order-1", "process", "tenant");

    // when - same calls as the ProcessInstanceElementCompleted/Terminated appliers
    elementInstanceState.removeInstance(100L);
    elementInstanceState.deleteProcessInstanceKeyMappingByBusinessId(
        "order-1", "process", "tenant", 100L);

    // then
    assertThat(
            elementInstanceState.hasActiveProcessInstanceWithBusinessId(
                "order-1", "process", "tenant", key -> false))
        .isFalse();
  }

  /** Same calls as the ProcessInstanceElementActivating applier for a root process instance. */
  private void createRootProcessInstanceWithBusinessId(
      final long processInstanceKey,
      final String businessId,
      final String bpmnProcessId,
      final String tenantId) {
    final var record =
        createProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setProcessInstanceKey(processInstanceKey)
            .setBpmnProcessId(bpmnProcessId)
            .setTenantId(tenantId)
            .setBusinessId(businessId);
    elementInstanceState.newInstance(
        processInstanceKey, record, ProcessInstanceIntent.ELEMENT_ACTIVATING);
    elementInstanceState.insertProcessInstanceKeyByBusinessId(
        businessId, bpmnProcessId, tenantId, processInstanceKey);
  }

  @Test
  void shouldReturnZeroIfNoSequenceFlowIsTaken() {
    // given
    createFlowScopes();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        OTHER_FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);

    // when
    final var number =
        elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);

    // then
    assertThat(number).isZero();
  }

  @Test
  void shouldIncrementNumberOfTakenSequenceFlows() {
    // given
    createFlowScopes();

    // when
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    // then
    assertThat(
            elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID))
        .isEqualTo(2);
    assertThat(
            elementInstanceState.getNumberOfTakenSequenceFlows(
                OTHER_FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID))
        .isZero();
    assertThat(
            elementInstanceState.getNumberOfTakenSequenceFlows(
                FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID))
        .isZero();
  }

  @Test
  void shouldReturnNumberPerTakenSequenceFlows() {
    // given
    createFlowScopes();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);

    // when / then
    assertThat(
            elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID))
        .isEqualTo(2);
    assertThat(elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY)).isEqualTo(3);
  }

  @Test
  void shouldReturnTakenSequenceFlows() {
    // given
    createFlowScopes();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    // when
    final var takenSequenceFlows =
        elementInstanceState.getTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);

    // then
    assertThat(takenSequenceFlows)
        .containsExactlyInAnyOrder(SEQUENCE_FLOW_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);
  }

  @Test
  void shouldVisitTakenSequenceFlowsOfFlowScope() {
    // given
    createFlowScopes();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        OTHER_FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);

    // when
    final List<Tuple> visited = new ArrayList<>();
    elementInstanceState.visitTakenSequenceFlows(
        FLOW_SCOPE_KEY,
        (flowScopeKey, gatewayId, sequenceFlowId, number) ->
            visited.add(
                tuple(
                    flowScopeKey,
                    bufferAsString(gatewayId),
                    bufferAsString(sequenceFlowId),
                    number)));

    // then
    assertThat(visited)
        .containsExactlyInAnyOrder(
            tuple(FLOW_SCOPE_KEY, "gateway-1", "flow-1", 2),
            tuple(FLOW_SCOPE_KEY, "gateway-2", "flow-2", 1));
  }

  @Test
  void shouldDecrementNumbers() {
    // given
    createFlowScopes();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        OTHER_FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);

    // when
    elementInstanceState.decrementNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);

    // then
    assertThat(
            elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID))
        .isZero();
    assertThat(
            elementInstanceState.getNumberOfTakenSequenceFlows(
                OTHER_FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID))
        .isEqualTo(1);
    assertThat(
            elementInstanceState.getNumberOfTakenSequenceFlows(
                FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID))
        .isEqualTo(1);
  }

  @Test
  void shouldDecrementNumbersButKeepRemaining() {
    // given
    createFlowScopes();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    // when
    elementInstanceState.decrementNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);

    // then
    assertThat(
            elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID))
        .isEqualTo(1);
    assertThat(elementInstanceState.getTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID))
        .containsExactly(SEQUENCE_FLOW_ELEMENT_ID);
  }

  @Test
  void shouldDecrementSingleSequenceFlow() {
    // given
    createFlowScopes();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    // when
    elementInstanceState.decrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.decrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    // then
    final List<Tuple> visited = new ArrayList<>();
    elementInstanceState.visitTakenSequenceFlows(
        FLOW_SCOPE_KEY,
        (flowScopeKey, gatewayId, sequenceFlowId, number) ->
            visited.add(tuple(bufferAsString(sequenceFlowId), number)));
    assertThat(visited).containsExactly(tuple("flow-1", 1));
  }

  @Test
  void shouldRemoveNumbersWhenDecrementing() {
    // given
    createFlowScopes();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    // when
    elementInstanceState.decrementNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);

    // then - counts all entries of the flow scope, so a leftover entry would show up
    assertThat(elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY)).isZero();
  }

  @Test
  void shouldRemoveNumbersWhenRemovingTheScope() {
    // given
    createFlowScopes();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        OTHER_FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);

    // when
    elementInstanceState.removeInstance(FLOW_SCOPE_KEY);

    // then
    assertThat(elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY)).isZero();
    assertThat(elementInstanceState.getNumberOfTakenSequenceFlows(OTHER_FLOW_SCOPE_KEY))
        .isEqualTo(1);
  }

  /** Creates the flow scopes the tests take sequence flows in. */
  /** Gateways and the sequence flows they take are contained in a flow scope, e.g. a subprocess. */
  private void createFlowScopes() {
    for (final long flowScopeKey : List.of(FLOW_SCOPE_KEY, OTHER_FLOW_SCOPE_KEY)) {
      elementInstanceState.newInstance(
          flowScopeKey,
          createProcessInstanceRecord().setBpmnElementType(BpmnElementType.SUB_PROCESS),
          ProcessInstanceIntent.ELEMENT_ACTIVATED);
    }
  }

  protected ElementInstance createParentWithChildren(
      final long parentKey, final long... childKeys) {
    final var parentInstance =
        elementInstanceState.newInstance(
            parentKey, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);
    for (final long childKey : childKeys) {
      elementInstanceState.newInstance(
          parentInstance,
          childKey,
          createProcessInstanceRecord().setElementId("child-" + childKey),
          ProcessInstanceIntent.ELEMENT_ACTIVATING);
    }
    return parentInstance;
  }

  protected void assertElementInstance(
      final ElementInstance elementInstance, final int childCount) {
    assertThat(elementInstance.getKey()).isEqualTo(100);
    assertThat(elementInstance.getState()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    assertThat(elementInstance.getJobKey()).isEqualTo(0);
    assertThat(elementInstance.canTerminate()).isTrue();
    assertThat(elementInstance.getNumberOfActiveElementInstances()).isEqualTo(childCount);
    assertProcessInstanceRecord(elementInstance.getValue());
  }

  protected void assertChildInstance(
      final ElementInstance childInstance, final long key, final String elementId) {
    assertThat(childInstance.getKey()).isEqualTo(key);
    assertThat(childInstance.getState()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(childInstance.getJobKey()).isEqualTo(0);
    assertThat(childInstance.canTerminate()).isTrue();
    assertThat(childInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    assertProcessInstanceRecord(childInstance.getValue(), wrapString(elementId));
  }

  protected ProcessInstanceRecord createProcessInstanceRecord() {
    final ProcessInstanceRecord processInstanceRecord = new ProcessInstanceRecord();
    processInstanceRecord.setElementId("startEvent");
    processInstanceRecord.setBpmnProcessId(wrapString("process1"));
    processInstanceRecord.setProcessInstanceKey(1000L);
    processInstanceRecord.setFlowScopeKey(1001L);
    processInstanceRecord.setVersion(1);
    processInstanceRecord.setProcessDefinitionKey(2);
    processInstanceRecord.setBpmnElementType(BpmnElementType.START_EVENT);

    return processInstanceRecord;
  }

  protected void assertProcessInstanceRecord(final ProcessInstanceRecord record) {
    assertProcessInstanceRecord(record, wrapString("startEvent"));
  }

  protected void assertProcessInstanceRecord(
      final ProcessInstanceRecord record, final DirectBuffer elementId) {
    assertThat(record.getElementIdBuffer()).isEqualTo(elementId);
    assertThat(record.getBpmnProcessIdBuffer()).isEqualTo(wrapString("process1"));
    assertThat(record.getProcessInstanceKey()).isEqualTo(1000L);
    assertThat(record.getFlowScopeKey()).isEqualTo(1001L);
    assertThat(record.getVersion()).isEqualTo(1);
    assertThat(record.getProcessDefinitionKey()).isEqualTo(2);
    assertThat(record.getBpmnElementType()).isEqualTo(BpmnElementType.START_EVENT);
  }
}
