/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnConditionalBehavior;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.RecordingTypedEventWriter;
import io.camunda.zeebe.engine.util.RecordingTypedEventWriter.RecordedEvent;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableSourceRecord;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValueAssert;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(ProcessingStateExtension.class)
final class VariableBehaviorTest {

  private static final ExpressionLanguage EXPRESSION_LANGUAGE =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private static final ScopedEvaluationContext DEFAULT_CONTEXT_LOOKUP =
      variableName -> Either.left(null);

  private final RecordingTypedEventWriter eventWriter = new RecordingTypedEventWriter();
  private final RecordingTypedCommandWriter commandWriter = new RecordingTypedCommandWriter();

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  private MutableVariableState state;
  private VariableBehavior behavior;

  @BeforeEach
  void beforeEach() {
    final var eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);
    final var stateWriter = new EventApplyingStateWriter(eventWriter, eventAppliers);
    final ExpressionProcessor expressionProcessor =
        new ExpressionProcessor(
            EXPRESSION_LANGUAGE,
            DEFAULT_CONTEXT_LOOKUP,
            EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT);
    // commandWriter is never called in tests, so we can pass null
    final var conditionalBehavior =
        new BpmnConditionalBehavior(
            processingState, null, expressionProcessor, EXPRESSION_LANGUAGE);

    state = processingState.getVariableState();
    behavior =
        new VariableBehavior(
            state,
            stateWriter,
            commandWriter,
            conditionalBehavior,
            processingState.getKeyGenerator());
  }

  @Test
  void shouldMergeLocalDocument() {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long childFooKey = 3;
    final long rootKey = 4;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    final Map<String, Object> document = Map.of("foo", "bar", "baz", "buz");
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    setVariable(childFooKey, childScopeKey, processDefinitionKey, "foo", "qux");

    // when
    behavior.mergeLocalDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        rootKey,
        bpmnProcessId,
        tenantId,
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
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId)
                  .hasRootProcessInstanceKey(rootKey);
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
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId)
                  .hasRootProcessInstanceKey(rootKey);
            });
  }

  @Test
  void shouldNotMergeLocalDocumentIfEmpty() {
    // given
    final long processDefinitionKey = 1;
    final long scopeKey = 1;
    final long rootKey = 2;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    final Map<String, Object> document = Map.of();
    setVariable(2, scopeKey, processDefinitionKey, "foo", "qux");

    // when
    behavior.mergeLocalDocument(
        scopeKey,
        processDefinitionKey,
        scopeKey,
        rootKey,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document));

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
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
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
        rootScopeKey,
        bpmnProcessId,
        tenantId,
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
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
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
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    final Map<String, Object> document = Map.of("foo", "bar", "buz", "baz");
    state.createScope(rootScopeKey, VariableState.NO_PARENT);
    state.createScope(parentScopeKey, rootScopeKey);
    state.createScope(childScopeKey, parentScopeKey);

    // when
    behavior.mergeDocument(
        childScopeKey,
        rootScopeKey,
        processDefinitionKey,
        rootScopeKey,
        bpmnProcessId,
        tenantId,
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
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            },
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("buz")
                  .hasValue("\"baz\"")
                  .hasScopeKey(rootScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(rootScopeKey)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldMergeDocumentWithoutUpdatingUnmodifiedVariable() {
    // given
    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
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
        rootScopeKey,
        bpmnProcessId,
        tenantId,
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
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldMergeDocumentWithoutPropagatingExistingVariables() {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long childFooKey = 3;
    final long rootKey = 5;
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
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
        rootKey,
        bpmnProcessId,
        tenantId,
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
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldNotMergeDocumentIfEmpty() {
    // given
    final int processDefinitionKey = 1;
    final int parentScopeKey = 1;
    final int childScopeKey = 2;
    final long rootKey = 5;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
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
        rootKey,
        bpmnProcessId,
        tenantId,
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
    final int rootProcessInstanceKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    final DirectBuffer variableName = BufferUtil.wrapString("foo");
    final DirectBuffer variableValue = packString("bar");
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);

    // when
    behavior.setLocalVariable(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        rootProcessInstanceKey,
        bpmnProcessId,
        tenantId,
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
                  .hasRootProcessInstanceKey(rootProcessInstanceKey)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldUpdateLocalVariable() {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long parentFooKey = 3;
    final long rootProcessInstanceKey = 4;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
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
        rootProcessInstanceKey,
        bpmnProcessId,
        tenantId,
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
                  .hasRootProcessInstanceKey(rootProcessInstanceKey)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldNotUpdateUnmodifiedVariables() {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long parentFooKey = 3;
    final long rootProcessInstanceKey = 4;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
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
        rootProcessInstanceKey,
        bpmnProcessId,
        tenantId,
        variableName,
        variableValue,
        0,
        variableValue.capacity());

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events).isEmpty();
  }

  @Test
  void shouldAssignCustomTenantOnCreateLocalVariable() {
    // given
    final var tenantId = "foo";

    final int processDefinitionKey = 1;
    final int parentScopeKey = 1;
    final int childScopeKey = 2;
    final int rootProcessInstanceKey = 3;

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
        rootProcessInstanceKey,
        bpmnProcessId,
        tenantId,
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
                  .hasRootProcessInstanceKey(rootProcessInstanceKey)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldAssignCustomTenantOnUpdateLocalVariable() {
    // given
    final var tenantId = "foo";

    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long parentFooKey = 3;
    final long rootProcessInstanceKey = 4;

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
        rootProcessInstanceKey,
        bpmnProcessId,
        tenantId,
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
                  .hasRootProcessInstanceKey(rootProcessInstanceKey)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldAssignCustomTenantOnMergeLocalDocument() {
    // given
    final var tenantId = "foo";

    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long childFooKey = 3;
    final long rootKey = 4;

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
        rootKey,
        bpmnProcessId,
        tenantId,
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
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
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
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldAssignCustomTenantOnMergeDocument() {
    // given
    final var tenantId = "foo";

    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;

    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final Map<String, Object> document = Map.of("foo", "bar", "buz", "baz");

    state.createScope(rootScopeKey, VariableState.NO_PARENT);
    state.createScope(parentScopeKey, rootScopeKey);
    state.createScope(childScopeKey, parentScopeKey);

    setVariable(123456L, parentScopeKey, processDefinitionKey, "foo", "qux");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        rootScopeKey,
        rootScopeKey,
        bpmnProcessId,
        tenantId,
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
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            },
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("foo")
                  .hasValue("\"bar\"")
                  .hasScopeKey(parentScopeKey)
                  .hasProcessDefinitionKey(processDefinitionKey)
                  .hasProcessInstanceKey(rootScopeKey)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldMergeDocumentWithVariableSource(final boolean isApiSource) {
    // given
    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;
    final long elementInstanceKey = 42;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    final Map<String, Object> document = Map.of("foo", "bar");
    state.createScope(rootScopeKey, VariableState.NO_PARENT);
    state.createScope(parentScopeKey, rootScopeKey);
    state.createScope(childScopeKey, parentScopeKey);
    final var variableSource =
        isApiSource ? VariableSourceRecord.api() : VariableSourceRecord.none();
    final var behavior = this.behavior.withVariableSource(variableSource);

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        rootScopeKey,
        rootScopeKey,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document));

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events).hasSize(1);
    final var event = events.getFirst();
    assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
    VariableRecordValueAssert.assertThat(event.value)
        .hasName("foo")
        .hasValue("\"bar\"")
        .hasScopeKey(rootScopeKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(rootScopeKey)
        .hasBpmnProcessId("process")
        .hasSource(variableSource);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldMergeLocalDocumentWithVariableSource(final boolean isApiSource) {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long rootKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    final Map<String, Object> document = Map.of("foo", "bar");
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    final var variableSource =
        isApiSource ? VariableSourceRecord.api() : VariableSourceRecord.none();
    final var behavior = this.behavior.withVariableSource(variableSource);

    // when
    behavior.mergeLocalDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        rootKey,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document));

    // then
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events).hasSize(1);
    final var event = events.getFirst();
    assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
    VariableRecordValueAssert.assertThat(event.value)
        .hasName("foo")
        .hasValue("\"bar\"")
        .hasScopeKey(childScopeKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(parentScopeKey)
        .hasBpmnProcessId("process")
        .hasTenantId(tenantId)
        .hasSource(variableSource);
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

  // ---- Cluster variable routing tests ----

  @Test
  void shouldRouteDottedClusterVariablePrefixToClusterVariableUpdateCommand() {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long rootKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    final DirectBuffer document =
        MsgPackUtil.asMsgPack("{\"camunda.vars.cluster.foo\": \"bar\", \"baz\": \"buz\"}");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        rootKey,
        bpmnProcessId,
        tenantId,
        document);

    // then — cluster variable command emitted with the stripped name
    final List<RecordedCommand> commands = commandWriter.getCommands();
    assertThat(commands).hasSize(1);
    final RecordedCommand cmd = commands.getFirst();
    assertThat(cmd.intent).isEqualTo(ClusterVariableIntent.UPDATE);
    final ClusterVariableRecord clusterRecord = (ClusterVariableRecord) cmd.value;
    assertThat(clusterRecord.getName()).isEqualTo("foo");
    assertThat(clusterRecord.getValue()).isEqualTo("\"bar\"");
    assertThat(clusterRecord.getScope()).isEqualTo(ClusterVariableScope.GLOBAL);

    // and — the prefixed entry is NOT written to local scope; the other entry is
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .extracting(e -> e.value.getName())
        .containsExactly("baz")
        .doesNotContain("camunda.vars.cluster.foo");
  }

  @Test
  void shouldRouteNestedClusterVariableMapToClusterVariableUpdateCommand() {
    // given — what Zeebe's output mapping produces for
    //   <zeebe:output source="=configuration" target="camunda.vars.cluster.clusterConfiguration" />
    //   <zeebe:output source="=configuration" target="configuration" />
    // i.e. a top-level "camunda" entry whose value is the nested vars→cluster→<name> map,
    // plus the sibling "configuration" entry.
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long rootKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);

    final String configurationJson =
        "{\"version\": 1, \"members\": {\"0\": {\"state\": \"ACTIVE\"}}, "
            + "\"clusterId\": \"cid-1\"}";
    final DirectBuffer document =
        MsgPackUtil.asMsgPack(
            "{\"camunda\": {\"vars\": {\"cluster\": {\"clusterConfiguration\": "
                + configurationJson
                + "}}}, \"configuration\": "
                + configurationJson
                + "}");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        rootKey,
        bpmnProcessId,
        tenantId,
        document);

    // then — exactly one cluster variable update is emitted, with the inner map intact
    final List<RecordedCommand> commands = commandWriter.getCommands();
    assertThat(commands).hasSize(1);
    final RecordedCommand cmd = commands.getFirst();
    assertThat(cmd.intent).isEqualTo(ClusterVariableIntent.UPDATE);
    final ClusterVariableRecord clusterRecord = (ClusterVariableRecord) cmd.value;
    assertThat(clusterRecord.getName()).isEqualTo("clusterConfiguration");
    assertThat(clusterRecord.getScope()).isEqualTo(ClusterVariableScope.GLOBAL);
    // The cluster variable's value must equal the original configuration map — NOT the
    // wrapping {vars: {cluster: ...}} structure.
    MsgPackUtil.assertEquality(clusterRecord.getValueBuffer(), configurationJson);

    // and — only the "configuration" sibling is written to the local scope; the "camunda"
    // entry is dropped entirely so we don't end up with a stale nested process variable.
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .extracting(e -> e.value.getName())
        .containsExactly("configuration")
        .doesNotContain("camunda");
  }

  @Test
  void shouldRouteEachClusterVariableInNestedMap() {
    // given — multiple cluster variables produced by sibling output mappings
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long rootKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    final DirectBuffer document =
        MsgPackUtil.asMsgPack(
            "{\"camunda\": {\"vars\": {\"cluster\": "
                + "{\"alpha\": 1, \"beta\": \"two\", \"gamma\": [1, 2, 3]}}}}");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        rootKey,
        bpmnProcessId,
        tenantId,
        document);

    // then — one update per cluster variable, in iteration order of the inner map
    final List<RecordedCommand> commands = commandWriter.getCommands();
    assertThat(commands).hasSize(3);
    assertThat(commands)
        .allSatisfy(c -> assertThat(c.intent).isEqualTo(ClusterVariableIntent.UPDATE));

    final List<String> names =
        commands.stream()
            .map(c -> ((ClusterVariableRecord) c.value).getName())
            .collect(Collectors.toList());
    assertThat(names).containsExactlyInAnyOrder("alpha", "beta", "gamma");

    // pick gamma and verify its value survived the round trip as msgpack
    final ClusterVariableRecord gamma =
        commands.stream()
            .map(c -> (ClusterVariableRecord) c.value)
            .filter(r -> r.getName().equals("gamma"))
            .findFirst()
            .orElseThrow();
    MsgPackUtil.assertEquality(gamma.getValueBuffer(), "[1, 2, 3]");

    // and — no local variables created (the camunda entry was extracted entirely)
    assertThat(getFollowUpEvents()).isEmpty();
  }

  @Test
  void shouldRouteToTenantScopeWhenNonDefaultTenantIdProvided() {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long rootKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = "tenant-A";
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    final DirectBuffer document =
        MsgPackUtil.asMsgPack("{\"camunda\": {\"vars\": {\"cluster\": {\"foo\": \"bar\"}}}}");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        rootKey,
        bpmnProcessId,
        tenantId,
        document);

    // then
    final List<RecordedCommand> commands = commandWriter.getCommands();
    assertThat(commands).hasSize(1);
    final ClusterVariableRecord clusterRecord = (ClusterVariableRecord) commands.getFirst().value;
    assertThat(clusterRecord.getScope()).isEqualTo(ClusterVariableScope.TENANT);
    assertThat(clusterRecord.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldRoundTripRealisticClusterConfigurationViaNestedMap() {
    // given — a configuration map shaped like the real Camunda cluster configuration:
    //   nested members → partitions → exporting → exporters, with lots of MAP16-sized maps.
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long rootKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);

    final String exporters =
        "{\"MetricsExporter\": {\"metadataVersion\": 0, \"state\": \"ENABLED\", "
            + "\"initializedFrom\": null}, "
            + "\"rdbms\": {\"metadataVersion\": 0, \"state\": \"ENABLED\", "
            + "\"initializedFrom\": null}}";
    final String partition =
        "{\"state\": \"ACTIVE\", \"priority\": 3, \"config\": {\"exporting\": {\"state\": "
            + "\"EXPORTING\", \"exporters\": "
            + exporters
            + "}, \"initialized\": true}}";
    final String memberZero =
        "{\"version\": 3, \"lastUpdated\": \"2026-05-07T15:12:31.974980556Z\", "
            + "\"state\": \"ACTIVE\", \"partitions\": {\"1\": "
            + partition
            + ", \"3\": "
            + partition
            + "}}";
    final String memberOne =
        "{\"version\": 2, \"lastUpdated\": \"2026-05-07T15:12:31.902373435Z\", "
            + "\"state\": \"ACTIVE\", \"partitions\": {\"1\": "
            + partition
            + ", \"2\": "
            + partition
            + "}}";
    final String memberTwo =
        "{\"version\": 0, \"lastUpdated\": \"-1000000000-01-01T00:00:00Z\", "
            + "\"state\": \"ACTIVE\", \"partitions\": {\"1\": "
            + partition
            + ", \"2\": "
            + partition
            + ", \"3\": "
            + partition
            + "}}";
    final String memberThree =
        "{\"version\": 6, \"lastUpdated\": \"2026-05-07T15:12:31.765840645Z\", "
            + "\"state\": \"ACTIVE\", \"partitions\": {\"2\": "
            + partition
            + ", \"3\": "
            + partition
            + "}}";
    final String configurationJson =
        "{\"version\": 1, "
            + "\"members\": {\"0\": "
            + memberZero
            + ", \"1\": "
            + memberOne
            + ", \"2\": "
            + memberTwo
            + ", \"3\": "
            + memberThree
            + "}, "
            + "\"lastChange\": null, \"pendingChanges\": null, "
            + "\"routingState\": {\"version\": 1, \"requestHandling\": {\"partitionCount\": 3}, "
            + "\"messageCorrelation\": {\"partitionCount\": 3}}, "
            + "\"clusterId\": \"9a4702c9-f47b-42c8-a16f-e786f77c462d\", "
            + "\"incarnationNumber\": 0, \"uninitialized\": false}";

    final DirectBuffer document =
        MsgPackUtil.asMsgPack(
            "{\"camunda\": {\"vars\": {\"cluster\": {\"clusterConfiguration\": "
                + configurationJson
                + "}}}, \"configuration\": "
                + configurationJson
                + "}");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        rootKey,
        bpmnProcessId,
        tenantId,
        document);

    // then — exactly one cluster variable update with the FULL configuration map intact
    final List<RecordedCommand> commands = commandWriter.getCommands();
    assertThat(commands).hasSize(1);
    final ClusterVariableRecord clusterRecord = (ClusterVariableRecord) commands.getFirst().value;
    assertThat(clusterRecord.getName()).isEqualTo("clusterConfiguration");
    // Round-trip: the value bytes we extracted must decode to the same JSON we encoded
    MsgPackUtil.assertEquality(clusterRecord.getValueBuffer(), configurationJson);

    // and — local scope writes only the sibling "configuration", never a stale "camunda"
    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .extracting(e -> e.value.getName())
        .containsExactly("configuration")
        .doesNotContain("camunda");
  }

  @Test
  void shouldNotEmitClusterCommandForUnrelatedNestedNamespace() {
    // given — same shape but rooted under a different name; must NOT trigger routing
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long rootKey = 3;
    final DirectBuffer bpmnProcessId = BufferUtil.wrapString("process");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    final DirectBuffer document =
        MsgPackUtil.asMsgPack("{\"unrelated\": {\"vars\": {\"cluster\": {\"foo\": \"bar\"}}}}");

    // when
    behavior.mergeDocument(
        childScopeKey,
        processDefinitionKey,
        parentScopeKey,
        rootKey,
        bpmnProcessId,
        tenantId,
        document);

    // then — no cluster command, the "unrelated" variable lands in local scope as-is
    assertThat(commandWriter.getCommands()).isEmpty();
    assertThat(getFollowUpEvents()).extracting(e -> e.value.getName()).containsExactly("unrelated");
  }

  /**
   * Records {@link ClusterVariableIntent#UPDATE} (and any other) commands appended via {@link
   * TypedCommandWriter}. Cloned via {@link Records#cloneValue} to defend against record reuse in
   * the production code under test.
   */
  private static final class RecordingTypedCommandWriter implements TypedCommandWriter {

    private final List<RecordedCommand> commands = new ArrayList<>();

    List<RecordedCommand> getCommands() {
      return commands;
    }

    @Override
    public void appendNewCommand(final Intent intent, final RecordValue value) {
      commands.add(new RecordedCommand(-1, intent, Records.cloneValue(value)));
    }

    @Override
    public void appendFollowUpCommand(
        final long key, final Intent intent, final RecordValue value) {
      commands.add(new RecordedCommand(key, intent, Records.cloneValue(value)));
    }

    @Override
    public void appendFollowUpCommand(
        final long key,
        final Intent intent,
        final RecordValue value,
        final FollowUpCommandMetadata metadata) {
      commands.add(new RecordedCommand(key, intent, Records.cloneValue(value)));
    }

    @Override
    public boolean canWriteCommandOfLength(final int commandLength) {
      return true;
    }
  }

  private record RecordedCommand(long key, Intent intent, RecordValue value) {}
}
