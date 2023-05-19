/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.RecordingTypedEventWriter;
import io.camunda.zeebe.engine.util.RecordingTypedEventWriter.RecordedEvent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValueAssert;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class VariableBehaviorTest {

  private final RecordingTypedEventWriter eventWriter = new RecordingTypedEventWriter();

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  private MutableVariableState state;
  private VariableBehavior behavior;

  @BeforeEach
  void beforeEach() {
    final var eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);
    final var stateWriter = new EventApplyingStateWriter(eventWriter, eventAppliers);

    state = processingState.getVariableState();
    behavior = new VariableBehavior(state, stateWriter, processingState.getKeyGenerator());
  }

  @Test
  void shouldMergeLocalDocument() {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long childFooKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final Map<String, Object> document = Map.of("foo", "bar", "baz", "buz");
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    setVariable(childFooKey, childScopeKey, processDefinitionKey, "foo", "qux");

    // when
    behavior.mergeLocalDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        bpmnProcessId,
        MsgPackUtil.asMsgPack(document));

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .satisfiesExactlyInAnyOrder(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("baz")
                  .hasValue("\"buz\"")
                  .hasScopeKey(childScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(parentScopeKey)
                  .hasBpmnProcessId("process");
            },
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.key).isEqualTo(childFooKey);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("foo")
                  .hasValue("\"bar\"")
                  .hasScopeKey(childScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(parentScopeKey)
                  .hasBpmnProcessId("process");
            });
  }

  @Test
  void shouldNotMergeLocalDocumentIfEmpty() {
    // given
    final long processDefinitionKey = 1;
    final long scopeKey = 1;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final Map<String, Object> document = Map.of();
    setVariable(2, scopeKey, processDefinitionKey, "foo", "qux");

    // when
    behavior.mergeLocalDocument(
        scopeKey, processDefinitionKey, scopeKey, bpmnProcessId, MsgPackUtil.asMsgPack(document));

    // then
    assertThat(getFollowUpEvents()).isEmpty();
  }

  @Test
  void shouldMergeDocumentWithoutPropagatingMoreThanOnce() {
    // given
    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;
    final long parentFooKey = 4;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final Map<String, Object> document = Map.of("foo", "bar");
    state.createScope(rootScopeKey, VariableState.NO_PARENT);
    state.createScope(parentScopeKey, rootScopeKey);
    state.createScope(childScopeKey, parentScopeKey);
    setVariable(parentFooKey, parentScopeKey, processDefinitionKey, "foo", "qux");
    setVariable(5, rootScopeKey, processDefinitionKey, "foo", "biz");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        rootScopeKey,
        bpmnProcessId,
        MsgPackUtil.asMsgPack(document));

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .satisfiesExactlyInAnyOrder(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.key).isEqualTo(parentFooKey);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("foo")
                  .hasValue("\"bar\"")
                  .hasScopeKey(parentScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(rootScopeKey)
                  .hasBpmnProcessId("process");
            });
  }

  @Test
  void shouldMergeDocumentPropagatingToRoot() {
    // given
    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final Map<String, Object> document = Map.of("foo", "bar", "buz", "baz");
    state.createScope(rootScopeKey, VariableState.NO_PARENT);
    state.createScope(parentScopeKey, rootScopeKey);
    state.createScope(childScopeKey, parentScopeKey);

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        rootScopeKey,
        bpmnProcessId,
        MsgPackUtil.asMsgPack(document));

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .satisfiesExactlyInAnyOrder(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("foo")
                  .hasValue("\"bar\"")
                  .hasScopeKey(rootScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(rootScopeKey)
                  .hasBpmnProcessId("process");
            },
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("buz")
                  .hasValue("\"baz\"")
                  .hasScopeKey(rootScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(rootScopeKey)
                  .hasBpmnProcessId("process");
            });
  }

  @Test
  void shouldMergeDocumentWithoutUpdatingUnmodifiedVariable() {
    // given
    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final Map<String, Object> document = Map.of("foo", "bar", "buz", "baz");
    state.createScope(rootScopeKey, VariableState.NO_PARENT);
    state.createScope(parentScopeKey, rootScopeKey);
    state.createScope(childScopeKey, parentScopeKey);
    setVariable(4, rootScopeKey, processDefinitionKey, "foo", "bar");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        rootScopeKey,
        bpmnProcessId,
        MsgPackUtil.asMsgPack(document));

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .satisfiesExactlyInAnyOrder(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("buz")
                  .hasValue("\"baz\"")
                  .hasScopeKey(rootScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(rootScopeKey)
                  .hasBpmnProcessId("process");
            });
  }

  @Test
  void shouldMergeDocumentWithoutPropagatingExistingVariables() {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long childFooKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final Map<String, Object> document = Map.of("foo", "bar");
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    setVariable(childFooKey, childScopeKey, processDefinitionKey, "foo", "qux");
    setVariable(4, parentScopeKey, processDefinitionKey, "foo", "biz");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        bpmnProcessId,
        MsgPackUtil.asMsgPack(document));

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .satisfiesExactlyInAnyOrder(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.key).isEqualTo(childFooKey);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("foo")
                  .hasValue("\"bar\"")
                  .hasScopeKey(childScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(parentScopeKey)
                  .hasBpmnProcessId("process");
            });
  }

  @Test
  void shouldNotMergeDocumentIfEmpty() {
    // given
    final int processDefinitionKey = 1;
    final int parentScopeKey = 1;
    final int childScopeKey = 2;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final Map<String, Object> document = Map.of();
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    setVariable(3, parentScopeKey, processDefinitionKey, "foo", "qux");
    setVariable(4, childScopeKey, processDefinitionKey, "foo", "bar");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        bpmnProcessId,
        MsgPackUtil.asMsgPack(document));

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events).isEmpty();
  }

  @Test
  void shouldCreateLocalVariable() {
    // given
    final int processDefinitionKey = 1;
    final int parentScopeKey = 1;
    final int childScopeKey = 2;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final DirectBuffer variableName = BufferUtil.wrapString("foo");
    final DirectBuffer variableValue = packString("bar");
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);

    // when
    behavior.setLocalVariable(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        bpmnProcessId,
        variableName,
        variableValue,
        0,
        variableValue.capacity());

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .satisfiesExactlyInAnyOrder(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("foo")
                  .hasValue("\"bar\"")
                  .hasScopeKey(childScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(parentScopeKey)
                  .hasBpmnProcessId("process");
            });
  }

  @Test
  void shouldUpdateLocalVariable() {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long parentFooKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final DirectBuffer variableName = BufferUtil.wrapString("foo");
    final DirectBuffer variableValue = packString("bar");
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    setVariable(parentFooKey, parentScopeKey, processDefinitionKey, "foo", "qux");

    // when
    behavior.setLocalVariable(
        parentScopeKey,
        processDefinitionKey,
        parentScopeKey,
        bpmnProcessId,
        variableName,
        variableValue,
        0,
        variableValue.capacity());

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .satisfiesExactlyInAnyOrder(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.key).isEqualTo(parentFooKey);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("foo")
                  .hasValue("\"bar\"")
                  .hasScopeKey(parentScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(parentScopeKey)
                  .hasBpmnProcessId("process");
            });
  }

  @Test
  void shouldNotUpdateUnmodifiedVariables() {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long parentFooKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final DirectBuffer variableName = BufferUtil.wrapString("foo");
    final DirectBuffer variableValue = packString("bar");
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    setVariable(parentFooKey, parentScopeKey, processDefinitionKey, "foo", "bar");

    // when
    behavior.setLocalVariable(
        parentScopeKey,
        processDefinitionKey,
        parentScopeKey,
        bpmnProcessId,
        variableName,
        variableValue,
        0,
        variableValue.capacity());

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private List<RecordedEvent<VariableRecordValue>> getFollowUpEvents() {
    return eventWriter.getEvents().stream()
        .filter(e -> e.value instanceof VariableRecordValue)
        .map(e -> (RecordedEvent<VariableRecordValue>) e)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("SameParameterValue")
  private void setVariable(
      final long key,
      final long scopeKey,
      final long processDefinitionKey,
      final String name,
      final String value) {
    final DirectBuffer nameBuffer = BufferUtil.wrapString(name);
    state.setVariableLocal(key, scopeKey, processDefinitionKey, nameBuffer, packString(value));
  }

  private DirectBuffer packString(final String value) {
    return MsgPackUtil.encodeMsgPack(b -> b.packString(value));
  }
}
