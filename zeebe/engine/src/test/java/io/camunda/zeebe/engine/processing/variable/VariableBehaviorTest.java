/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static io.camunda.zeebe.protocol.record.value.ProtectionMode.REDACT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnConditionalBehavior;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.RecordingTypedEventWriter;
import io.camunda.zeebe.engine.util.RecordingTypedEventWriter.RecordedEvent;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableSourceRecord;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValueAssert;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            state, stateWriter, conditionalBehavior, processingState.getKeyGenerator());
  }

  @Test
  void shouldMergeLocalDocument() throws VariableValidationException {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long childFooKey = 3;
    final long rootKey = 4;
    final int storageOrdinal = 5;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

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
                  .hasRootProcessInstanceKey(rootKey)
                  .hasStorageOrdinal(storageOrdinal)
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
                  .hasRootProcessInstanceKey(rootKey)
                  .hasStorageOrdinal(storageOrdinal)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldNotMergeLocalDocumentIfEmpty() throws VariableValidationException {
    // given
    final long processDefinitionKey = 1;
    final long scopeKey = 1;
    final long rootKey = 2;
    final int storageOrdinal = 3;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

    // then
    assertThat(getFollowUpEvents()).isEmpty();
  }

  @Test
  void shouldMergeDocumentWithoutPropagatingMoreThanOnce() throws VariableValidationException {
    // given
    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;
    final long parentFooKey = 4;
    final int storageOrdinal = 5;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

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
                  .hasRootProcessInstanceKey(rootScopeKey)
                  .hasStorageOrdinal(storageOrdinal)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldMergeDocumentPropagatingToRoot() throws VariableValidationException {
    // given
    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;
    final int storageOrdinal = 4;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

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
                  .hasRootProcessInstanceKey(rootScopeKey)
                  .hasStorageOrdinal(storageOrdinal)
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
                  .hasRootProcessInstanceKey(rootScopeKey)
                  .hasStorageOrdinal(storageOrdinal)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldMergeDocumentWithoutUpdatingUnmodifiedVariable() throws VariableValidationException {
    // given
    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;
    final int storageOrdinal = 4;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

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
                  .hasRootProcessInstanceKey(rootScopeKey)
                  .hasStorageOrdinal(storageOrdinal)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldMergeDocumentWithoutPropagatingExistingVariables() throws VariableValidationException {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long childFooKey = 3;
    final long rootKey = 5;
    final int storageOrdinal = 6;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

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
                  .hasRootProcessInstanceKey(rootKey)
                  .hasStorageOrdinal(storageOrdinal)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldNotMergeDocumentIfEmpty() throws VariableValidationException {
    // given
    final int processDefinitionKey = 1;
    final int parentScopeKey = 1;
    final int childScopeKey = 2;
    final long rootKey = 5;
    final int storageOrdinal = 6;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

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
    final int storageOrdinal = 4;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        variableName,
        variableValue,
        0,
        variableValue.capacity(),
        VariableMetadataLookup.NONE);

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
                  .hasStorageOrdinal(storageOrdinal)
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
    final int storageOrdinal = 5;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        variableName,
        variableValue,
        0,
        variableValue.capacity(),
        VariableMetadataLookup.NONE);

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
                  .hasStorageOrdinal(storageOrdinal)
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
    final int storageOrdinal = 5;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        variableName,
        variableValue,
        0,
        variableValue.capacity(),
        VariableMetadataLookup.NONE);

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
    final int storageOrdinal = 4;

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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        variableName,
        variableValue,
        0,
        variableValue.capacity(),
        VariableMetadataLookup.NONE);

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
                  .hasStorageOrdinal(storageOrdinal)
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
    final int storageOrdinal = 5;

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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        variableName,
        variableValue,
        0,
        variableValue.capacity(),
        VariableMetadataLookup.NONE);

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
                  .hasStorageOrdinal(storageOrdinal)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldAssignCustomTenantOnMergeLocalDocument() throws VariableValidationException {
    // given
    final var tenantId = "foo";

    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long childFooKey = 3;
    final long rootKey = 4;
    final int storageOrdinal = 5;

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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

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
                  .hasRootProcessInstanceKey(rootKey)
                  .hasStorageOrdinal(storageOrdinal)
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
                  .hasRootProcessInstanceKey(rootKey)
                  .hasStorageOrdinal(storageOrdinal)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @Test
  void shouldAssignCustomTenantOnMergeDocument() throws VariableValidationException {
    // given
    final var tenantId = "foo";

    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;
    final int storageOrdinal = 4;

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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

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
                  .hasRootProcessInstanceKey(rootScopeKey)
                  .hasStorageOrdinal(storageOrdinal)
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
                  .hasRootProcessInstanceKey(rootScopeKey)
                  .hasStorageOrdinal(storageOrdinal)
                  .hasBpmnProcessId("process")
                  .hasTenantId(tenantId);
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldMergeDocumentWithVariableSource(final boolean isApiSource)
      throws VariableValidationException {
    // given
    final long processDefinitionKey = 1;
    final long rootScopeKey = 1;
    final long parentScopeKey = 2;
    final long childScopeKey = 3;
    final int storageOrdinal = 4;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

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
        .hasRootProcessInstanceKey(rootScopeKey)
        .hasStorageOrdinal(storageOrdinal)
        .hasBpmnProcessId("process")
        .hasSource(variableSource);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldMergeLocalDocumentWithVariableSource(final boolean isApiSource)
      throws VariableValidationException {
    // given
    final long processDefinitionKey = 1;
    final long parentScopeKey = 1;
    final long childScopeKey = 2;
    final long rootKey = 3;
    final int storageOrdinal = 4;
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
        storageOrdinal,
        bpmnProcessId,
        tenantId,
        MsgPackUtil.asMsgPack(document),
        VariableMetadataLookup.NONE);

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
        .hasRootProcessInstanceKey(rootKey)
        .hasStorageOrdinal(storageOrdinal)
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
  @Test
  void shouldCreateLocalVariableWithProtectionModes() {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);
    final var value = packString("bar");

    // when
    behavior.setLocalVariable(
        scopeKey,
        1L,
        scopeKey,
        scopeKey,
        0,
        BufferUtil.wrapString("process"),
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        BufferUtil.wrapString("foo"),
        value,
        0,
        value.capacity(),
        lookup(Map.of("foo", redact())));

    // then
    assertThat(getFollowUpEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
              assertThat(event.value.getMetadata().getProtectionModes()).containsExactly(REDACT);
            });
  }

  @Test
  void shouldCreateEachMergedVariableWithItsOwnProtectionModes()
      throws VariableValidationException {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);

    // when
    mergeLocalDocument(
        scopeKey, Map.of("foo", "bar", "baz", "buz", "qux", "quux"), Map.of("foo", redact()));

    // then
    assertThat(getFollowUpEvents())
        .hasSize(3)
        .allSatisfy(event -> assertThat(event.intent).isEqualTo(VariableIntent.CREATED))
        .extracting(
            event -> event.value.getName(), event -> event.value.getMetadata().getProtectionModes())
        .containsExactlyInAnyOrder(
            tuple("foo", Set.of(REDACT)), tuple("baz", Set.of()), tuple("qux", Set.of()));
  }

  @Test
  void shouldCreateVariableWithoutProtectionModesByDefault() throws VariableValidationException {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);

    // when
    behavior.mergeLocalDocument(
        scopeKey,
        1L,
        scopeKey,
        scopeKey,
        0,
        BufferUtil.wrapString("process"),
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        MsgPackUtil.asMsgPack(Map.of("foo", "bar")),
        VariableMetadataLookup.NONE);

    // then
    assertThat(getFollowUpEvents())
        .singleElement()
        .satisfies(event -> assertThat(event.value.getMetadata().getProtectionModes()).isEmpty());
  }

  @Test
  void shouldUpdateProtectionModesWithoutChangingValue() throws VariableValidationException {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);
    setVariable(2L, scopeKey, 1L, "foo", "bar");

    // when
    mergeLocalDocument(scopeKey, Map.of("foo", "bar"), Map.of("foo", redact()));

    // then
    assertThat(getFollowUpEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.key).isEqualTo(2L);
              assertThat(event.value.getMetadata().getProtectionModes()).containsExactly(REDACT);
            });
  }

  @Test
  void shouldNotUpdateVariableWhenValueAndProtectionModesAreUnchanged()
      throws VariableValidationException {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);
    setProtectedVariable(2L, scopeKey, "foo", "bar");

    // when
    mergeLocalDocument(scopeKey, Map.of("foo", "bar"), Map.of("foo", redact()));

    // then
    assertThat(getFollowUpEvents()).isEmpty();
  }

  @Test
  void shouldUpdateValueAndKeepProtectionModesPassedByCaller() throws VariableValidationException {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);
    setProtectedVariable(2L, scopeKey, "foo", "qux");

    // when
    mergeLocalDocument(scopeKey, Map.of("foo", "bar"), Map.of("foo", redact()));

    // then
    assertThat(getFollowUpEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.value.getMetadata().getProtectionModes()).containsExactly(REDACT);
            });
  }

  @Test
  void shouldReplaceProtectionModesWithTheOnesPassedByCaller() throws VariableValidationException {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);
    setProtectedVariable(2L, scopeKey, "foo", "bar");

    // when
    mergeLocalDocument(scopeKey, Map.of("foo", "bar"), Map.of());

    // then
    assertThat(getFollowUpEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.value.getMetadata().getProtectionModes()).isEmpty();
            });
  }

  @Test
  void shouldUpdateProtectionModesInParentScope() throws VariableValidationException {
    // given
    final var parentScopeKey = 1L;
    final var childScopeKey = 2L;
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    setVariable(3L, parentScopeKey, 1L, "foo", "bar");

    // when
    behavior.mergeDocument(
        childScopeKey,
        1L,
        parentScopeKey,
        parentScopeKey,
        0,
        BufferUtil.wrapString("process"),
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        MsgPackUtil.asMsgPack(Map.of("foo", "bar")),
        lookup(Map.of("foo", redact())));

    // then
    assertThat(getFollowUpEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.value.getScopeKey()).isEqualTo(parentScopeKey);
              assertThat(event.value.getMetadata().getProtectionModes()).containsExactly(REDACT);
            });
  }

  @Test
  void shouldPersistProtectionModesInState() throws VariableValidationException {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);

    // when
    mergeLocalDocument(scopeKey, Map.of("foo", "bar"), Map.of("foo", redact()));

    // then
    assertThat(
            state
                .getVariableInstanceLocal(scopeKey, BufferUtil.wrapString("foo"))
                .getMetadata()
                .getProtectionModes())
        .containsExactly(REDACT);
  }

  @Test
  void shouldKeepProtectionModesWhenUpdatingValueWithoutMetadata()
      throws VariableValidationException {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);
    setProtectedVariable(2L, scopeKey, "foo", "qux");

    // when
    mergeLocalDocument(scopeKey, Map.of("foo", "bar"), VariableMetadataLookup.NONE);

    // then
    assertThat(getFollowUpEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.value.getMetadata().getProtectionModes()).containsExactly(REDACT);
            });
  }

  @Test
  void shouldNotUpdateUnchangedValueWithoutMetadata() throws VariableValidationException {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);
    setProtectedVariable(2L, scopeKey, "foo", "bar");

    // when
    mergeLocalDocument(scopeKey, Map.of("foo", "bar"), VariableMetadataLookup.NONE);

    // then
    assertThat(getFollowUpEvents()).isEmpty();
  }

  @Test
  void shouldKeepProtectionModesOfVariablesWithoutMetadataInLookup()
      throws VariableValidationException {
    // given
    final var scopeKey = 1L;
    state.createScope(scopeKey, VariableState.NO_PARENT);
    setProtectedVariable(2L, scopeKey, "foo", "qux");
    final VariableMetadataLookup onlyBaz =
        name -> "baz".equals(BufferUtil.bufferAsString(name)) ? redact() : null;

    // when
    mergeLocalDocument(scopeKey, Map.of("foo", "bar", "baz", "buz"), onlyBaz);

    // then
    assertThat(getFollowUpEvents())
        .extracting(
            event -> event.intent,
            event -> event.value.getName(),
            event -> event.value.getMetadata().getProtectionModes())
        .containsExactlyInAnyOrder(
            tuple(VariableIntent.UPDATED, "foo", Set.of(REDACT)),
            tuple(VariableIntent.CREATED, "baz", Set.of(REDACT)));
  }

  @Test
  void shouldKeepProtectionModesInParentScopeWithoutMetadata() throws VariableValidationException {
    // given
    final var parentScopeKey = 1L;
    final var childScopeKey = 2L;
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    setProtectedVariable(3L, parentScopeKey, "foo", "qux");

    // when
    behavior.mergeDocument(
        childScopeKey,
        1L,
        parentScopeKey,
        parentScopeKey,
        0,
        BufferUtil.wrapString("process"),
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        MsgPackUtil.asMsgPack(Map.of("foo", "bar")),
        VariableMetadataLookup.NONE);

    // then
    assertThat(getFollowUpEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.value.getMetadata().getProtectionModes()).containsExactly(REDACT);
            });
  }

  private void mergeLocalDocument(
      final long scopeKey,
      final Map<String, Object> document,
      final Map<String, VariableMetadataRecord> metadataByName)
      throws VariableValidationException {
    mergeLocalDocument(scopeKey, document, lookup(metadataByName));
  }

  private void mergeLocalDocument(
      final long scopeKey,
      final Map<String, Object> document,
      final VariableMetadataLookup metadataLookup)
      throws VariableValidationException {
    behavior.mergeLocalDocument(
        scopeKey,
        1L,
        scopeKey,
        scopeKey,
        0,
        BufferUtil.wrapString("process"),
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        MsgPackUtil.asMsgPack(document),
        metadataLookup);
  }

  private static VariableMetadataLookup lookup(
      final Map<String, VariableMetadataRecord> metadataByName) {
    return name ->
        metadataByName.getOrDefault(BufferUtil.bufferAsString(name), new VariableMetadataRecord());
  }

  private static VariableMetadataRecord redact() {
    return new VariableMetadataRecord().setProtectionModes(Set.of(REDACT));
  }

  private void setProtectedVariable(
      final long key, final long scopeKey, final String name, final String value) {
    state.setVariableLocal(
        key, scopeKey, 1L, BufferUtil.wrapString(name), packString(value), redact());
  }

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
