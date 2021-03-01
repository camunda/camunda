/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.processing.streamprocessor.writers.EventApplyingStateWriter;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.ZeebeDbState;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.appliers.EventAppliers;
import io.zeebe.engine.state.immutable.VariableState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.engine.util.RecordingTypedEventWriter;
import io.zeebe.engine.util.RecordingTypedEventWriter.RecordedEvent;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.protocol.record.value.VariableRecordValueAssert;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * The following tests currently assert both that the right follow up events are produced AND that
 * the state was correctly modified. This is because right now the behaviour modifies the state, and
 * relies on the listener to produce follow up events.
 *
 * <p>Once event sourcing has been applied to variables, we can stop asserting on the state and rely
 * purely on the events - other tests should ensure the state is correctly built up from the events.
 */
final class VariableDocumentBehaviorTest {

  private final RecordingTypedEventWriter eventWriter = new RecordingTypedEventWriter();

  private ZeebeDb<ZbColumnFamilies> db;
  private ZeebeState zeebeState;
  private MutableVariableState state;
  private VariableDocumentBehavior behavior;

  @BeforeEach
  void beforeEach(final @TempDir File directory) {
    db = DefaultZeebeDbFactory.defaultFactory().createDb(directory);
    zeebeState = new ZeebeDbState(db, db.createContext());

    state = zeebeState.getVariableState();
    behavior = new VariableDocumentBehavior(state);
  }

  @AfterEach
  void afterEach() {
    CloseHelper.close(db);
  }

  @Test
  void shouldMergeLocalDocument() {
    // given
    final int workflowKey = 1;
    final int parentScopeKey = 1;
    final int childScopeKey = 2;
    final Map<String, Object> document = Map.of("foo", "bar", "baz", "buz");
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    final long childFooKey = setVariable(childScopeKey, workflowKey, "foo", "qux");

    // when
    listenForStateChanges();
    behavior.mergeLocalDocument(childScopeKey, workflowKey, MsgPackUtil.asMsgPack(document));

    // then
    assertThat(getScopeVariables(childScopeKey))
        .containsOnly(entry("foo", "bar"), entry("baz", "buz"));
    assertThat(getScopeVariables(parentScopeKey)).isEmpty();

    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .satisfiesExactlyInAnyOrder(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("baz")
                  .hasValue("\"buz\"")
                  .hasScopeKey(childScopeKey)
                  .hasWorkflowKey(workflowKey)
                  .hasWorkflowInstanceKey(parentScopeKey);
            },
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.UPDATED);
              assertThat(event.key).isEqualTo(childFooKey);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("foo")
                  .hasValue("\"bar\"")
                  .hasScopeKey(childScopeKey)
                  .hasWorkflowKey(workflowKey)
                  .hasWorkflowInstanceKey(parentScopeKey);
            });
  }

  @Test
  void shouldNotMergeLocalDocumentIfEmpty() {
    // given
    final int workflowKey = 1;
    final int scopeKey = 1;
    final Map<String, Object> document = Map.of();
    setVariable(scopeKey, workflowKey, "foo", "qux");

    // when
    listenForStateChanges();
    behavior.mergeLocalDocument(scopeKey, workflowKey, MsgPackUtil.asMsgPack(document));

    // then
    assertThat(getScopeVariables(scopeKey)).containsOnly(entry("foo", "qux"));
    assertThat(getFollowUpEvents()).isEmpty();
  }

  @Test
  void shouldMergeDocumentWithoutPropagatingMoreThanOnce() {
    // given
    final int workflowKey = 1;
    final int rootScopeKey = 1;
    final int parentScopeKey = 2;
    final int childScopeKey = 3;
    final Map<String, Object> document = Map.of("foo", "bar");
    state.createScope(rootScopeKey, VariableState.NO_PARENT);
    state.createScope(parentScopeKey, rootScopeKey);
    state.createScope(childScopeKey, parentScopeKey);
    final long parentFooKey = setVariable(parentScopeKey, workflowKey, "foo", "qux");
    setVariable(rootScopeKey, workflowKey, "foo", "biz");

    // when
    listenForStateChanges();
    behavior.mergeDocument(childScopeKey, workflowKey, MsgPackUtil.asMsgPack(document));

    // then
    assertThat(getScopeVariables(parentScopeKey)).containsOnly(entry("foo", "bar"));
    assertThat(getScopeVariables(rootScopeKey)).containsOnly(entry("foo", "biz"));

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
                  .hasWorkflowKey(workflowKey)
                  .hasWorkflowInstanceKey(rootScopeKey);
            });
  }

  @Test
  void shouldMergeDocumentPropagatingToRoot() {
    // given
    final int workflowKey = 1;
    final int rootScopeKey = 1;
    final int parentScopeKey = 2;
    final int childScopeKey = 3;
    final Map<String, Object> document = Map.of("foo", "bar", "buz", "baz");
    state.createScope(rootScopeKey, VariableState.NO_PARENT);
    state.createScope(parentScopeKey, rootScopeKey);
    state.createScope(childScopeKey, parentScopeKey);

    // when
    listenForStateChanges();
    behavior.mergeDocument(childScopeKey, workflowKey, MsgPackUtil.asMsgPack(document));

    // then
    assertThat(getScopeVariables(rootScopeKey))
        .containsOnly(entry("foo", "bar"), entry("buz", "baz"));
    assertThat(getScopeVariables(parentScopeKey)).isEmpty();
    assertThat(getScopeVariables(childScopeKey)).isEmpty();

    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events)
        .satisfiesExactlyInAnyOrder(
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("foo")
                  .hasValue("\"bar\"")
                  .hasScopeKey(rootScopeKey)
                  .hasWorkflowKey(workflowKey)
                  .hasWorkflowInstanceKey(rootScopeKey);
            },
            event -> {
              assertThat(event.intent).isEqualTo(VariableIntent.CREATED);
              VariableRecordValueAssert.assertThat(event.value)
                  .hasName("buz")
                  .hasValue("\"baz\"")
                  .hasScopeKey(rootScopeKey)
                  .hasWorkflowKey(workflowKey)
                  .hasWorkflowInstanceKey(rootScopeKey);
            });
  }

  @Test
  void shouldMergeDocumentWithoutPropagatingExistingVariables() {
    // given
    final int workflowKey = 1;
    final int parentScopeKey = 1;
    final int childScopeKey = 2;
    final Map<String, Object> document = Map.of("foo", "bar");
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    final long childFooKey = setVariable(childScopeKey, workflowKey, "foo", "qux");
    setVariable(parentScopeKey, workflowKey, "foo", "biz");

    // when
    listenForStateChanges();
    behavior.mergeDocument(childScopeKey, workflowKey, MsgPackUtil.asMsgPack(document));

    // then
    assertThat(getScopeVariables(childScopeKey)).containsOnly(entry("foo", "bar"));
    assertThat(getScopeVariables(parentScopeKey)).containsOnly(entry("foo", "biz"));

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
                  .hasWorkflowKey(workflowKey)
                  .hasWorkflowInstanceKey(parentScopeKey);
            });
  }

  @Test
  void shouldNotMergeDocumentIfEmpty() {
    // given
    final int workflowKey = 1;
    final int parentScopeKey = 1;
    final int childScopeKey = 2;
    final Map<String, Object> document = Map.of();
    state.createScope(parentScopeKey, VariableState.NO_PARENT);
    state.createScope(childScopeKey, parentScopeKey);
    setVariable(parentScopeKey, workflowKey, "foo", "qux");
    setVariable(childScopeKey, workflowKey, "foo", "bar");

    // when
    listenForStateChanges();
    behavior.mergeDocument(childScopeKey, workflowKey, MsgPackUtil.asMsgPack(document));

    // then
    assertThat(getScopeVariables(parentScopeKey)).containsOnly(entry("foo", "qux"));
    assertThat(getScopeVariables(childScopeKey)).containsOnly(entry("foo", "bar"));

    final List<RecordedEvent<VariableRecordValue>> events = getFollowUpEvents();
    assertThat(events).isEmpty();
  }

  /**
   * This sets up the test to start listening for follow up events. It's usually a good idea to do
   * this after the initial setup so you can easily ignore those events.
   */
  private void listenForStateChanges() {
    final EventAppliers eventApplier = new EventAppliers(zeebeState);
    final EventApplyingStateWriter stateWriter =
        new EventApplyingStateWriter(eventWriter, eventApplier);
    final UpdateVariableStreamWriter listener = new UpdateVariableStreamWriter(stateWriter);
    state.setListener(listener);
  }

  @SuppressWarnings("unchecked")
  private List<RecordedEvent<VariableRecordValue>> getFollowUpEvents() {
    return eventWriter.getEvents().stream()
        .filter(e -> e.value instanceof VariableRecordValue)
        .map(e -> (RecordedEvent<VariableRecordValue>) e)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("SameParameterValue")
  private long setVariable(
      final long scopeKey, final long workflowKey, final String name, final String value) {
    final DirectBuffer nameBuffer = BufferUtil.wrapString(name);
    state.setVariableLocal(scopeKey, workflowKey, nameBuffer, packString(value));
    return state.getVariableInstanceLocal(scopeKey, nameBuffer).getKey();
  }

  private DirectBuffer packString(final String value) {
    return MsgPackUtil.encodeMsgPack(b -> b.packString(value));
  }

  private Map<String, Object> getScopeVariables(final long scopeKey) {
    final DirectBuffer rawDocument = state.getVariablesLocalAsDocument(scopeKey);
    try (final DirectBufferInputStream input = new DirectBufferInputStream(rawDocument)) {
      return new ObjectMapper(new MessagePackFactory()).readValue(input, new TypeReference<>() {});
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
