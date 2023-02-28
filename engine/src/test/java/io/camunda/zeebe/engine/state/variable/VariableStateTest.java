/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.variable;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.camunda.zeebe.test.util.MsgPackUtil.assertEquality;
import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public final class VariableStateTest {

  @ClassRule public static final ProcessingStateRule ZEEBE_STATE_RULE = new ProcessingStateRule();
  private static final long PROCESS_KEY = 123;
  private static final AtomicLong PARENT_KEY = new AtomicLong(0);
  private static final AtomicLong CHILD_KEY = new AtomicLong(1);
  private static final AtomicLong SECOND_CHILD_KEY = new AtomicLong(2);
  private static final AtomicLong KEY_GENERATOR = new AtomicLong();
  private static MutableElementInstanceState elementInstanceState;
  private static MutableVariableState variableState;
  private long parent;
  private long child;
  private long child2;

  @BeforeClass
  public static void setUp() {
    final MutableProcessingState processingState = ZEEBE_STATE_RULE.getProcessingState();
    elementInstanceState = processingState.getElementInstanceState();
    variableState = processingState.getVariableState();
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
  public void shouldSetLocalVariable() {
    // given
    declareScope(parent);

    // when
    final long keyVarA = setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    final long keyVarB = setVariableLocal(parent, wrapString("b"), asMsgPack("2"));

    // then
    final VariableInstance varA = variableState.getVariableInstanceLocal(parent, wrapString("a"));
    assertThat(varA.getKey()).isEqualTo(keyVarA);
    assertEquality(varA.getValue(), "1");

    final VariableInstance varB = variableState.getVariableInstanceLocal(parent, wrapString("b"));
    assertThat(varB.getKey()).isEqualTo(keyVarB);
    assertEquality(varB.getValue(), "2");
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
    final long expectedKey = setVariableLocal(parent, wrapString("x"), wrapString("foo"));
    setVariableLocal(child, wrapString("x"), wrapString("foo"));

    // when
    final VariableInstance variable =
        variableState.getVariableInstanceLocal(parent, wrapString("x"));

    // then
    assertThat(variable).isNotNull();
    assertThat(variable.getValue()).isEqualTo(wrapString("foo"));
    assertThat(variable.getKey()).isEqualTo(expectedKey);
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

  private void declareScope(final long key) {
    declareScope(-1, key);
  }

  private void declareScope(final long parentKey, final long key) {
    final ElementInstance parent = elementInstanceState.getInstance(parentKey);

    final TypedRecord<ProcessInstanceRecord> record = mockTypedRecord(key, parentKey);
    elementInstanceState.newInstance(
        parent, key, record.getValue(), ProcessInstanceIntent.ELEMENT_ACTIVATING);
  }

  private TypedRecord<ProcessInstanceRecord> mockTypedRecord(final long key, final long parentKey) {
    final ProcessInstanceRecord processInstanceRecord = createProcessInstanceRecord(parentKey);

    final TypedRecord<ProcessInstanceRecord> typedRecord = mock(TypedRecord.class);
    when(typedRecord.getKey()).thenReturn(key);
    when(typedRecord.getValue()).thenReturn(processInstanceRecord);

    return typedRecord;
  }

  private ProcessInstanceRecord createProcessInstanceRecord(final long parentKey) {
    final ProcessInstanceRecord processInstanceRecord = new ProcessInstanceRecord();

    if (parentKey >= 0) {
      processInstanceRecord.setFlowScopeKey(parentKey);
    }

    return processInstanceRecord;
  }

  public long setVariableLocal(
      final long scopeKey, final DirectBuffer name, final DirectBuffer value) {
    final long key = KEY_GENERATOR.incrementAndGet();
    variableState.setVariableLocal(key, scopeKey, PROCESS_KEY, name, value);
    return key;
  }
}
