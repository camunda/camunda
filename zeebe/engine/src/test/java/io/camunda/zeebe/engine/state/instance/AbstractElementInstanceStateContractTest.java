/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every {@link MutableElementInstanceState} implementation must share for the element
 * instance itself: create, update, remove, get and children. Each backend runs these tests by
 * extending this class.
 */
abstract class AbstractElementInstanceStateContractTest {

  protected MutableElementInstanceState elementInstanceState;

  /** Creates the state under test. Called before each test. */
  protected abstract MutableElementInstanceState createElementInstanceState();

  /** The variable state the element instance state under test creates and removes scopes in. */
  protected abstract VariableState variableState();

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
