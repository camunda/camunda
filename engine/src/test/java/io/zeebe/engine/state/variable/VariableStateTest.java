/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.variable;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.test.util.MsgPackUtil.assertEquality;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.VariableState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public final class VariableStateTest {

  @ClassRule public static final ZeebeStateRule ZEEBE_STATE_RULE = new ZeebeStateRule();
  private static final long WORKFLOW_KEY = 123;
  private static final AtomicLong PARENT_KEY = new AtomicLong(0);
  private static final AtomicLong CHILD_KEY = new AtomicLong(1);
  private static final AtomicLong SECOND_CHILD_KEY = new AtomicLong(2);
  private static MutableElementInstanceState elementInstanceState;
  private static MutableVariableState variableState;
  private static RecordingVariableListener listener;
  private long parent;
  private long child;
  private long child2;

  @BeforeClass
  public static void setUp() {
    final ZeebeState zeebeState = ZEEBE_STATE_RULE.getZeebeState();
    elementInstanceState = zeebeState.getElementInstanceState();
    variableState = zeebeState.getVariableState();

    listener = new RecordingVariableListener();
    variableState.setListener(listener);
  }

  @Before
  public void beforeTest() {
    parent = PARENT_KEY.getAndIncrement();
    child = CHILD_KEY.getAndIncrement();
    child2 = SECOND_CHILD_KEY.getAndIncrement();
  }

  @After
  public void cleanUp() {
    elementInstanceState.removeInstance(child2);
    elementInstanceState.removeInstance(child);
    elementInstanceState.removeInstance(parent);
    listener.reset();
  }

  @Test
  public void shouldCollectVariablesAsDocument() {
    // given
    declareScope(parent);

    final DirectBuffer var1Value = asMsgPack("a", 1);
    setVariableLocal(parent, wrapString("var1"), var1Value);

    final DirectBuffer var2Value = asMsgPack("x", 10);
    setVariableLocal(parent, wrapString("var2"), var2Value);

    // when
    final DirectBuffer variablesDocument = variableState.getVariablesAsDocument(parent);

    // then
    assertEquality(variablesDocument, "{'var1': {'a': 1}, 'var2': {'x': 10}}");
  }

  @Test
  public void shouldCollectNoVariablesAsEmptyDocument() {
    // given
    declareScope(parent);

    // when
    final DirectBuffer variablesDocument = variableState.getVariablesAsDocument(parent);

    // then
    assertEquality(variablesDocument, "{}");
  }

  @Test
  public void shouldCollectVariablesFromMultipleScopes() {
    // given
    final long grandparent = parent;
    final long parent = child;
    final long child = child2;
    declareScope(grandparent);
    declareScope(grandparent, parent);
    declareScope(parent, child);

    setVariableLocal(grandparent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(parent, wrapString("b"), asMsgPack("2"));
    setVariableLocal(child, wrapString("c"), asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument = variableState.getVariablesAsDocument(child);

    // then
    assertEquality(variablesDocument, "{'a': 1, 'b': 2, 'c': 3}");
  }

  @Test
  public void shouldNotCollectHiddenVariables() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(parent, wrapString("b"), asMsgPack("2"));
    setVariableLocal(child, wrapString("b"), asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument = variableState.getVariablesAsDocument(child);

    // then
    assertEquality(variablesDocument, "{'a': 1, 'b': 3}");
  }

  @Test
  public void shouldNotCollectVariablesFromChildScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(child, wrapString("b"), asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument = variableState.getVariablesAsDocument(parent);

    // then
    assertEquality(variablesDocument, "{'a': 1}");
  }

  @Test
  public void shouldNotCollectVariablesInSiblingScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    declareScope(parent, child2);

    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(child, wrapString("b"), asMsgPack("2"));
    setVariableLocal(child2, wrapString("c"), asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument = variableState.getVariablesAsDocument(child);

    // then
    assertEquality(variablesDocument, "{'a': 1, 'b': 2}");
  }

  @Test
  public void shouldCollectLocalVariables() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(child, wrapString("b"), asMsgPack("3"));

    // then
    assertEquality(variableState.getVariablesLocalAsDocument(parent), "{'a': 1}");
    assertEquality(variableState.getVariablesLocalAsDocument(child), "{'b': 3}");
  }

  @Test
  public void shouldCollectVariablesByName() {
    // given
    declareScope(parent);

    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(parent, wrapString("b"), asMsgPack("2"));
    setVariableLocal(parent, wrapString("c"), asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument =
        variableState.getVariablesAsDocument(
            parent, Arrays.asList(wrapString("a"), wrapString("c")));

    // then
    assertEquality(variablesDocument, "{'a': 1, 'c': 3}");
  }

  @Test
  public void shouldCollectVariablesByNameFromMultipleScopes() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    declareScope(child, child2);

    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(child, wrapString("b"), asMsgPack("2"));
    setVariableLocal(child2, wrapString("c"), asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument =
        variableState.getVariablesAsDocument(
            child2, Arrays.asList(wrapString("a"), wrapString("c")));

    // then
    assertEquality(variablesDocument, "{'a': 1, 'c': 3}");
  }

  @Test
  public void shouldCollectOnlyExistingVariablesByName() {
    // given
    declareScope(parent);

    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));

    // when
    final DirectBuffer variablesDocument =
        variableState.getVariablesAsDocument(
            parent, Arrays.asList(wrapString("a"), wrapString("c")));

    // then
    assertEquality(variablesDocument, "{'a': 1}");
  }

  @Test
  public void shouldSetLocalVariablesFromDocument() {
    // given
    declareScope(parent);

    final DirectBuffer document = asMsgPack(b -> b.put("a", 1).put("b", 2));

    // when
    setVariablesLocalFromDocument(parent, document);

    // then
    final DirectBuffer varA = variableState.getVariableLocal(parent, wrapString("a"));
    assertEquality(varA, "1");

    final DirectBuffer varB = variableState.getVariableLocal(parent, wrapString("b"));
    assertEquality(varB, "2");
  }

  @Test
  public void shouldSetLocalVariablesFromDocumentInHierarchy() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    final DirectBuffer document = asMsgPack(b -> b.put("a", 1).put("b", 2));

    // when
    setVariablesLocalFromDocument(child, document);

    // then
    final DirectBuffer varA = variableState.getVariableLocal(child, wrapString("a"));
    assertEquality(varA, "1");
    Assertions.assertThat(variableState.getVariableLocal(parent, wrapString("a"))).isNull();

    final DirectBuffer varB = variableState.getVariableLocal(child, wrapString("b"));
    assertEquality(varB, "2");
    Assertions.assertThat(variableState.getVariableLocal(parent, wrapString("b"))).isNull();
  }

  @Test
  public void shouldSetLocalVariableFromDocumentAsObject() {
    // given
    declareScope(parent);

    final DirectBuffer document = asMsgPack(b -> b.put("var", Collections.singletonMap("a", 1)));

    // when
    setVariablesLocalFromDocument(parent, document);

    // then
    final DirectBuffer varA = variableState.getVariableLocal(parent, wrapString("var"));
    assertEquality(varA, "{'a': 1}");
  }

  @Test
  public void shouldOverwriteLocalVariableFromDocument() {
    // given
    declareScope(parent);

    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));

    final DirectBuffer document = asMsgPack("a", 2);

    // when
    setVariablesLocalFromDocument(parent, document);

    // then
    final DirectBuffer varA = variableState.getVariableLocal(parent, wrapString("a"));
    assertEquality(varA, "2");
  }

  @Test
  public void shouldSetLocalVariable() {
    // given
    declareScope(parent);

    // when
    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(parent, wrapString("b"), asMsgPack("2"));

    // then
    final DirectBuffer varA = variableState.getVariableLocal(parent, wrapString("a"));
    assertEquality(varA, "1");

    final DirectBuffer varB = variableState.getVariableLocal(parent, wrapString("b"));
    assertEquality(varB, "2");
  }

  @Test
  public void shouldGetNullForNonExistingVariable() {
    // given
    declareScope(parent);

    // when
    final DirectBuffer variableValue = variableState.getVariableLocal(parent, wrapString("a"));

    // then
    assertThat(variableValue).isNull();
  }

  @Test
  public void shouldRemoveAllVariablesForScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, wrapString("parentVar1"), asMsgPack("1"));
    setVariableLocal(child, wrapString("childVar1"), asMsgPack("2"));
    setVariableLocal(child, wrapString("childVar2"), asMsgPack("3"));

    // when
    variableState.removeAllVariables(child);

    // then
    final DirectBuffer document = variableState.getVariablesAsDocument(child);

    assertEquality(document, "{'parentVar1': 1}");
  }

  @Test
  public void shouldRemoveScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, wrapString("parentVar1"), asMsgPack("1"));
    setVariableLocal(child, wrapString("childVar1"), asMsgPack("2"));
    setVariableLocal(child, wrapString("childVar2"), asMsgPack("3"));

    // when
    variableState.removeScope(child);

    // then
    final DirectBuffer document = variableState.getVariablesAsDocument(child);

    assertEquality(document, "{}");
  }

  @Test
  public void shouldSetVariablesFromDocument() {
    // given
    final long grandparent = parent;
    final long parent = child;
    final long child = child2;
    declareScope(grandparent);
    declareScope(grandparent, parent);
    declareScope(parent, child);

    setVariableLocal(grandparent, wrapString("a"), asMsgPack("'should-overwrite-this'"));
    setVariableLocal(parent, wrapString("b"), asMsgPack("'should-overwrite-this'"));
    setVariableLocal(child, wrapString("c"), asMsgPack("'should-overwrite-this'"));

    final DirectBuffer document = asMsgPack(b -> b.put("a", 1).put("b", 2).put("c", 3).put("d", 4));

    // when
    setVariablesFromDocument(child, document);

    // then
    final DirectBuffer varA = variableState.getVariableLocal(grandparent, wrapString("a"));
    assertEquality(varA, "1");

    final DirectBuffer varB = variableState.getVariableLocal(parent, wrapString("b"));
    assertEquality(varB, "2");

    final DirectBuffer varC = variableState.getVariableLocal(child, wrapString("c"));
    assertEquality(varC, "3");

    final DirectBuffer varD = variableState.getVariableLocal(grandparent, wrapString("d"));
    assertEquality(varD, "4");
  }

  @Test
  public void shouldSetVariablesFromDocumentNotInChildScopes() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(child, wrapString("b"), asMsgPack("'should-not-overwrite-this'"));

    final DirectBuffer document = asMsgPack(b -> b.put("a", 1).put("b", 2));

    // when
    setVariablesFromDocument(parent, document);

    // then
    final DirectBuffer varA = variableState.getVariableLocal(parent, wrapString("a"));
    assertEquality(varA, "1");

    final DirectBuffer varBParent = variableState.getVariableLocal(parent, wrapString("b"));
    assertEquality(varBParent, "2");

    final DirectBuffer varBChild = variableState.getVariableLocal(child, wrapString("b"));
    assertEquality(varBChild, "'should-not-overwrite-this'");
  }

  @Test
  public void shouldSetVariablesFromDocumentAsObject() {
    // given
    declareScope(parent);

    final DirectBuffer document = asMsgPack(b -> b.put("a", Collections.singletonMap("x", 1)));

    // when
    setVariablesFromDocument(parent, document);

    // then
    final DirectBuffer varA = variableState.getVariableLocal(parent, wrapString("a"));
    assertEquality(varA, "{'x': 1}");
  }

  @Test
  public void shouldSetVariablesFromDocumentNotInParentScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    setVariableLocal(parent, wrapString("a"), asMsgPack("'should-not-overwrite-this'"));
    setVariableLocal(child, wrapString("a"), asMsgPack("'should-overwrite-this'"));

    final DirectBuffer document = asMsgPack(b -> b.put("a", 1).put("b", 2));

    // when
    setVariablesFromDocument(child, document);

    // then
    final DirectBuffer varParent = variableState.getVariableLocal(parent, wrapString("a"));
    assertEquality(varParent, "'should-not-overwrite-this'");

    final DirectBuffer newVarParent = variableState.getVariableLocal(parent, wrapString("b"));
    assertEquality(newVarParent, "2");

    final DirectBuffer varChild = variableState.getVariableLocal(child, wrapString("a"));
    assertEquality(varChild, "1");
  }

  @Test
  public void shouldReturnParentScopeKey() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    // when
    final long parentScopeKey = variableState.getParentScopeKey(child);

    // then
    assertThat(parentScopeKey).isEqualTo(parent);
  }

  @Test
  public void shouldReturnNoParentForRootScopeKey() {
    // given
    declareScope(parent);

    // when
    final long parentScopeKey = variableState.getParentScopeKey(parent);

    // then
    assertThat(parentScopeKey).isEqualTo(VariableState.NO_PARENT);
  }

  /** Making sure the method is reusable and does not leave data structures dirty */
  @Test
  public void shouldSetVariablesFromDocumentRepeatedly() {
    // given
    final long parent1 = parent;
    final long parent2 = child;
    declareScope(parent1);
    declareScope(parent2);

    setVariablesFromDocument(parent1, asMsgPack("{'a': 1, 'b': 2}"));

    // when
    setVariablesFromDocument(parent2, asMsgPack("{'x': 3}"));

    // then
    final DirectBuffer parent1Doc = variableState.getVariablesAsDocument(parent1);
    assertEquality(parent1Doc, "{'a': 1, 'b': 2}");

    final DirectBuffer parent2Doc = variableState.getVariablesAsDocument(parent2);
    assertEquality(parent2Doc, "{'x': 3}");
  }

  @Test
  public void shouldGetVariableByName() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    declareScope(child, child2);

    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(child, wrapString("b"), asMsgPack("2"));
    setVariableLocal(child2, wrapString("c"), asMsgPack("3"));

    // when
    final DirectBuffer variableFromLocalScope =
        cloneBuffer(variableState.getVariable(child2, wrapString("c")));

    final DirectBuffer variableFromParentScope =
        cloneBuffer(variableState.getVariable(child2, wrapString("b")));

    final DirectBuffer variableFromRootScope =
        cloneBuffer(variableState.getVariable(child2, wrapString("a")));

    final DirectBuffer variableFromChildScope = variableState.getVariable(parent, wrapString("b"));

    // then
    assertEquality(variableFromLocalScope, "3");
    assertEquality(variableFromParentScope, "2");
    assertEquality(variableFromRootScope, "1");
    assertThat(variableFromChildScope).isNull();
  }

  @Test
  public void shouldInvokeListenerOnCreate() {
    // given

    // when
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));

    // then
    assertThat(listener.created).hasSize(1);
    assertThat(listener.created.get(0).name).isEqualTo("x");
    assertThat(listener.created.get(0).value).isEqualTo("foo".getBytes());
    assertThat(listener.created.get(0).variableScopeKey).isEqualTo(parent);
    assertThat(listener.created.get(0).rootScopeKey).isEqualTo(parent);

    assertThat(listener.updated).isEmpty();
  }

  @Test
  public void shouldInvokeListenerOnUpdate() {
    // given

    // when
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));
    setVariableLocal(parent, wrapString("x"), wrapString("bar"));

    // then
    assertThat(listener.created).hasSize(1);

    assertThat(listener.updated).hasSize(1);
    assertThat(listener.updated.get(0).name).isEqualTo("x");
    assertThat(listener.updated.get(0).value).isEqualTo("bar".getBytes());
    assertThat(listener.updated.get(0).variableScopeKey).isEqualTo(parent);
    assertThat(listener.updated.get(0).rootScopeKey).isEqualTo(parent);
  }

  @Test
  public void shouldNotGetVariableInstanceLocal() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));
    setVariableLocal(child, wrapString("y"), wrapString("foo"));

    // when
    final VariableInstance variable =
        variableState.getVariableInstanceLocal(child, wrapString("x"));

    // then
    assertThat(variable).isNull();
  }

  @Test
  public void shouldGetVariableInstanceLocal() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));
    setVariableLocal(child, wrapString("x"), wrapString("foo"));

    // when
    final VariableInstance variable =
        variableState.getVariableInstanceLocal(parent, wrapString("x"));

    // then
    assertThat(variable).isNotNull();
    assertThat(variable.getValue()).isEqualTo(wrapString("foo"));
    assertThat(variable.getKey()).isPositive();
  }

  @Test
  public void shouldNotGetVariableInstanceLocalIfScopeDoesNotExist() {
    // given
    final long scopeKey = child + 1;
    declareScope(scopeKey);
    setVariableLocal(scopeKey, wrapString("x"), wrapString("foo"));

    // when
    variableState.removeScope(scopeKey);
    final VariableInstance variable =
        variableState.getVariableInstanceLocal(scopeKey, wrapString("x"));

    // then
    assertThat(variable).isNull();
  }

  @Test
  public void shouldNotInvokeListenerIfNotChanged() {
    // given

    // when
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));

    // then
    assertThat(listener.created).hasSize(1);

    assertThat(listener.updated).hasSize(0);
  }

  @Test
  public void shouldInvokeListenerIfSetVariablesLocalFromDocument() {
    // given
    final Map<String, Object> document = Map.of("x", "foo", "y", "bar");

    // when
    setVariablesLocalFromDocument(parent, asMsgPack(document));

    // then
    assertThat(listener.updated).isEmpty();
    assertThat(listener.created).hasSize(document.size());
    for (final Entry<String, Object> entry : document.entrySet()) {
      assertThat(listener.created)
          .anySatisfy(
              v -> {
                assertThat(v.name).isEqualTo(entry.getKey());
                assertThat(v.value).isEqualTo(stringToMsgpack((String) entry.getValue()));
                assertThat(v.rootScopeKey).isEqualTo(parent);
                assertThat(v.variableScopeKey).isEqualTo(parent);
              });
    }
  }

  @Test
  public void shouldInvokeListenerIfSetVariablesFromDocument() {
    // given
    final long parentScope = parent;
    final long childScope = child;

    declareScope(parentScope);
    declareScope(parentScope, childScope);

    setVariablesLocalFromDocument(childScope, asMsgPack("{'x':'foo'}"));

    // when
    setVariablesFromDocument(childScope, asMsgPack("{'x':'bar', 'y':'bar'}"));

    // then
    assertThat(listener.created).hasSize(2);
    assertThat(listener.created.get(1).name).isEqualTo("y");
    assertThat(listener.created.get(1).value).isEqualTo(stringToMsgpack("bar"));
    assertThat(listener.created.get(1).variableScopeKey).isEqualTo(parentScope);
    assertThat(listener.created.get(1).rootScopeKey).isEqualTo(parentScope);

    assertThat(listener.updated).hasSize(1);
    assertThat(listener.updated.get(0).name).isEqualTo("x");
    assertThat(listener.updated.get(0).value).isEqualTo(stringToMsgpack("bar"));
    assertThat(listener.updated.get(0).variableScopeKey).isEqualTo(childScope);
    assertThat(listener.updated.get(0).rootScopeKey).isEqualTo(parentScope);
  }

  @Test
  public void shouldSetTemporaryVariables() {
    // when
    variableState.setTemporaryVariables(parent, wrapString("a"));
    variableState.setTemporaryVariables(child, wrapString("b"));

    // then
    Assertions.assertThat(variableState.getTemporaryVariables(parent)).isEqualTo(wrapString("a"));
    Assertions.assertThat(variableState.getTemporaryVariables(child)).isEqualTo(wrapString("b"));
  }

  @Test
  public void shouldRemoveTemporaryVariables() {
    // given
    variableState.setTemporaryVariables(parent, wrapString("a"));
    variableState.setTemporaryVariables(child, wrapString("b"));

    // when
    variableState.removeTemporaryVariables(parent);

    // then
    Assertions.assertThat(variableState.getTemporaryVariables(parent)).isNull();
    Assertions.assertThat(variableState.getTemporaryVariables(child)).isEqualTo(wrapString("b"));
  }

  @Test
  public void shouldReuseVariableKeyOnUpdate() {
    // given

    // when
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));
    setVariableLocal(parent, wrapString("x"), wrapString("bar"));

    // then
    final long variableKey = listener.created.get(0).key;
    assertThat(variableKey).isGreaterThan(0);
    assertThat(listener.updated.get(0).key).isEqualTo(variableKey);
  }

  private byte[] stringToMsgpack(final String value) {
    return MsgPackUtil.encodeMsgPack(b -> b.packString(value)).byteArray();
  }

  private void declareScope(final long key) {
    declareScope(-1, key);
  }

  private void declareScope(final long parentKey, final long key) {
    final ElementInstance parent = elementInstanceState.getInstance(parentKey);

    final TypedRecord<WorkflowInstanceRecord> record = mockTypedRecord(key, parentKey);
    elementInstanceState.newInstance(
        parent, key, record.getValue(), WorkflowInstanceIntent.ELEMENT_ACTIVATING);
  }

  private TypedRecord<WorkflowInstanceRecord> mockTypedRecord(
      final long key, final long parentKey) {
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord(parentKey);

    final TypedRecord<WorkflowInstanceRecord> typedRecord = mock(TypedRecord.class);
    when(typedRecord.getKey()).thenReturn(key);
    when(typedRecord.getValue()).thenReturn(workflowInstanceRecord);

    return typedRecord;
  }

  private WorkflowInstanceRecord createWorkflowInstanceRecord(final long parentKey) {
    final WorkflowInstanceRecord workflowInstanceRecord = new WorkflowInstanceRecord();

    if (parentKey >= 0) {
      workflowInstanceRecord.setFlowScopeKey(parentKey);
    }

    return workflowInstanceRecord;
  }

  private void setVariablesFromDocument(final long scope, final DirectBuffer document) {
    variableState.setVariablesFromDocument(scope, WORKFLOW_KEY, document);
  }

  private void setVariablesLocalFromDocument(final long scope, final DirectBuffer document) {
    variableState.setVariablesLocalFromDocument(scope, WORKFLOW_KEY, document);
  }

  public void setVariableLocal(
      final long scopeKey, final DirectBuffer name, final DirectBuffer value) {
    variableState.setVariableLocal(
        scopeKey, WORKFLOW_KEY, name, 0, name.capacity(), value, 0, value.capacity());
  }

  private static class RecordingVariableListener implements DbVariableState.VariableListener {

    private final List<VariableChange> created = new ArrayList<>();
    private final List<VariableChange> updated = new ArrayList<>();

    @Override
    public void onCreate(
        final long key,
        final long workflowKey,
        final DirectBuffer name,
        final DirectBuffer value,
        final long variableScopeKey,
        final long rootScopeKey) {
      final VariableChange change =
          new VariableChange(
              key,
              bufferAsString(name),
              BufferUtil.bufferAsArray(value),
              variableScopeKey,
              rootScopeKey);
      created.add(change);
    }

    @Override
    public void onUpdate(
        final long key,
        final long workflowKey,
        final DirectBuffer name,
        final DirectBuffer value,
        final long variableScopeKey,
        final long rootScopeKey) {
      final VariableChange change =
          new VariableChange(
              key,
              bufferAsString(name),
              BufferUtil.bufferAsArray(value),
              variableScopeKey,
              rootScopeKey);
      updated.add(change);
    }

    public void reset() {
      updated.clear();
      created.clear();
    }

    private static class VariableChange {

      private final long key;
      private final String name;
      private final byte[] value;
      private final long variableScopeKey;
      private final long rootScopeKey;

      VariableChange(
          final long key,
          final String name,
          final byte[] value,
          final long variableScopeKey,
          final long rootScopeKey) {
        this.key = key;
        this.name = name;
        this.value = value;
        this.variableScopeKey = variableScopeKey;
        this.rootScopeKey = rootScopeKey;
      }
    }
  }
}
