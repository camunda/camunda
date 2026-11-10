/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import static io.camunda.zeebe.protocol.record.value.ProtectionMode.REDACT;
import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.camunda.zeebe.test.util.MsgPackUtil.assertEquality;
import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.immutable.VariableState.Variable;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every {@link MutableVariableState} implementation must share. Each backend runs these
 * tests by extending this class.
 *
 * <p>Scopes are element instances, created through the element instance state that is wired with
 * the variable state under test, like in the engine. Their records carry the element instance path
 * the engine sets on activation.
 */
public abstract class AbstractVariableStateContractTest {

  protected static final long PROCESS_KEY = 123;
  private static final AtomicLong KEY_GENERATOR = new AtomicLong();

  protected MutableElementInstanceState elementInstanceState;
  protected MutableVariableState variableState;
  private final Map<Long, List<List<Long>>> elementInstancePaths = new HashMap<>();
  private long parent;
  private long child;
  private long child2;

  /**
   * Creates the element instance state under test, whose scopes {@link #variableState()} manages.
   * Called before each test.
   */
  protected abstract MutableElementInstanceState createElementInstanceState();

  /** The variable state under test; called after {@link #createElementInstanceState()}. */
  protected abstract MutableVariableState variableState();

  @BeforeEach
  void setUpStates() {
    elementInstanceState = createElementInstanceState();
    variableState = variableState();
    parent = 100;
    child = 101;
    child2 = 102;
  }

  @Test
  void shouldCollectVariablesAsDocument() {
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
  void shouldCollectNoVariablesAsEmptyDocument() {
    // given
    declareScope(parent);

    // when
    final DirectBuffer variablesDocument = variableState.getVariablesAsDocument(parent);

    // then
    assertEquality(variablesDocument, "{}");
  }

  @Test
  void shouldCollectVariablesFromMultipleScopes() {
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
  void shouldNotCollectHiddenVariables() {
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
  void shouldNotCollectVariablesFromChildScope() {
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
  void shouldNotCollectVariablesInSiblingScope() {
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
  void shouldCollectLocalVariables() {
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
  void shouldCollectVariablesByName() {
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
  void shouldCollectVariablesByNameFromMultipleScopes() {
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
  void shouldCollectOnlyExistingVariablesByName() {
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
  void shouldCollectVariablesByNameWithDuplicateNamesInRequest() {
    // given
    declareScope(parent);

    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(parent, wrapString("b"), asMsgPack("2"));

    // when — "a" appears twice, as would happen if a client sends a duplicate fetchVariable name
    final DirectBuffer variablesDocument =
        variableState.getVariablesAsDocument(
            parent, Arrays.asList(wrapString("a"), wrapString("b"), wrapString("a")));

    // then — map32 header count must equal the number of entries written, not names.size()
    assertEquality(variablesDocument, "{'a': 1, 'b': 2}");
  }

  @Test
  void shouldSetLocalVariable() {
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
  void shouldGetNullForNonExistingVariable() {
    // given
    declareScope(parent);

    // when
    final DirectBuffer variableValue = variableState.getVariableLocal(parent, wrapString("a"));

    // then
    assertThat(variableValue).isNull();
  }

  @Test
  void shouldRemoveAllVariablesForScope() {
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
  void shouldRemoveScope() {
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
  void shouldReturnParentScopeKey() {
    // given
    declareScope(parent);
    declareScope(parent, child);

    // when
    final long parentScopeKey = variableState.getParentScopeKey(child);

    // then
    assertThat(parentScopeKey).isEqualTo(parent);
  }

  @Test
  void shouldReturnNoParentForRootScopeKey() {
    // given
    declareScope(parent);

    // when
    final long parentScopeKey = variableState.getParentScopeKey(parent);

    // then
    assertThat(parentScopeKey).isEqualTo(VariableState.NO_PARENT);
  }

  @Test
  void shouldGetVariableByName() {
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
  void shouldNotGetVariableInstanceLocal() {
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
  void shouldGetVariableInstanceLocal() {
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
  void shouldNotGetVariableInstanceLocalIfScopeDoesNotExist() {
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
  void shouldGetVariablesLocalAtGlobalScope() {
    // given
    declareScope(parent);

    final long keyVariableA = setVariableLocal(parent, wrapString("a"), wrapString("1"));
    final long keyVariableB = setVariableLocal(parent, wrapString("b"), wrapString("2"));
    final long keyVariableC = setVariableLocal(parent, wrapString("c"), wrapString("3"));

    // when
    final List<Variable> variablesLocal = variableState.getVariablesLocal(parent);

    // then
    assertThat(variablesLocal)
        .hasSize(3)
        .extracting(Variable::key, Variable::scopeKey, Variable::name, Variable::value)
        .containsExactlyInAnyOrder(
            tuple(keyVariableA, parent, wrapString("a"), wrapString("1")),
            tuple(keyVariableB, parent, wrapString("b"), wrapString("2")),
            tuple(keyVariableC, parent, wrapString("c"), wrapString("3")));
  }

  @Test
  void shouldGetVariablesLocalAtLocalScopeOnly() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    declareScope(child, child2);

    setVariableLocal(parent, wrapString("a"), wrapString("1"));
    final long keyVariableB = setVariableLocal(child, wrapString("b"), wrapString("2"));
    final long keyVariableC = setVariableLocal(child, wrapString("c"), wrapString("3"));
    setVariableLocal(child2, wrapString("d"), wrapString("4"));

    // when
    final List<Variable> variablesLocal = variableState.getVariablesLocal(child);

    // then
    assertThat(variablesLocal)
        .hasSize(2)
        .extracting(Variable::key, Variable::scopeKey, Variable::name, Variable::value)
        .describedAs("Expected to encounter only the variables scoped at the child scope")
        .containsExactlyInAnyOrder(
            tuple(keyVariableB, child, wrapString("b"), wrapString("2")),
            tuple(keyVariableC, child, wrapString("c"), wrapString("3")));
  }

  @Test
  void shouldStoreMetadataWithVariable() {
    // given
    declareScope(parent);
    final var metadata = new VariableMetadataRecord().setProtectionModes(Set.of(REDACT));

    // when
    variableState.setVariableLocal(
        KEY_GENERATOR.incrementAndGet(),
        parent,
        PROCESS_KEY,
        wrapString("x"),
        wrapString("foo"),
        metadata);

    // then
    assertThat(variableState.getVariableInstanceLocal(parent, wrapString("x")).getMetadata())
        .satisfies(stored -> assertThat(stored.getProtectionModes()).containsExactly(REDACT));
  }

  @Test
  void shouldKeepMetadataWhenVariableIsSetWithoutMetadata() {
    // given
    declareScope(parent);
    final var metadata = new VariableMetadataRecord().setProtectionModes(Set.of(REDACT));
    variableState.setVariableLocal(
        KEY_GENERATOR.incrementAndGet(),
        parent,
        PROCESS_KEY,
        wrapString("x"),
        wrapString("foo"),
        metadata);

    // when
    setVariableLocal(parent, wrapString("x"), wrapString("bar"));

    // then
    final var stored = variableState.getVariableInstanceLocal(parent, wrapString("x"));
    assertThat(stored.getValue()).isEqualTo(wrapString("bar"));
    assertThat(stored.getMetadata().getProtectionModes()).containsExactly(REDACT);
  }

  @Test
  void shouldCreateVariableWithoutMetadataWhenSetWithoutMetadata() {
    // given
    declareScope(parent);

    // when
    setVariableLocal(parent, wrapString("x"), wrapString("foo"));

    // then
    assertThat(variableState.getVariableInstanceLocal(parent, wrapString("x")).getMetadata())
        .satisfies(stored -> assertThat(stored.getProtectionModes()).isEmpty());
  }

  @Test
  void shouldGetVariableFromNearestScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    declareScope(child, child2);
    setVariableLocal(parent, wrapString("x"), asMsgPack("1"));
    setVariableLocal(child, wrapString("x"), asMsgPack("2"));

    // when
    final DirectBuffer fromGrandchild =
        cloneBuffer(variableState.getVariable(child2, wrapString("x")));
    final DirectBuffer fromParent = cloneBuffer(variableState.getVariable(parent, wrapString("x")));

    // then
    assertEquality(fromGrandchild, "2");
    assertEquality(fromParent, "1");
  }

  @Test
  void shouldCollectVariablesByNameFromNearestScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(parent, wrapString("b"), asMsgPack("2"));
    setVariableLocal(child, wrapString("a"), asMsgPack("3"));

    // when
    final DirectBuffer variablesDocument =
        variableState.getVariablesAsDocument(
            child, Arrays.asList(wrapString("a"), wrapString("b")));

    // then
    assertEquality(variablesDocument, "{'a': 3, 'b': 2}");
  }

  @Test
  void shouldSeeUpdatedAndRemovedVariables() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));
    setVariableLocal(child, wrapString("a"), asMsgPack("2"));
    assertEquality(variableState.getVariable(child, wrapString("a")), "2");

    // when
    variableState.removeAllVariables(child);
    setVariableLocal(parent, wrapString("a"), asMsgPack("3"));

    // then
    assertEquality(variableState.getVariable(child, wrapString("a")), "3");
    assertEquality(variableState.getVariablesAsDocument(child), "{'a': 3}");
  }

  @Test
  void shouldNotFindParentOfRemovedScope() {
    // given
    declareScope(parent);
    declareScope(parent, child);
    setVariableLocal(parent, wrapString("a"), asMsgPack("1"));

    // when
    elementInstanceState.removeInstance(child);

    // then
    assertThat(variableState.getParentScopeKey(child)).isEqualTo(VariableState.NO_PARENT);
    assertThat(variableState.getVariable(child, wrapString("a"))).isNull();
    assertEquality(variableState.getVariablesAsDocument(child), "{}");
  }

  @Test
  void shouldStoreAndRemoveVariableDocumentState() {
    // given
    declareScope(parent);
    final var record =
        new VariableDocumentRecord()
            .setScopeKey(parent)
            .setVariables(asMsgPack("{'a': 1}"))
            .setUpdateSemantics(VariableDocumentUpdateSemantic.LOCAL);

    // when
    variableState.storeVariableDocumentState(5L, record);

    // then
    assertThat(variableState.findVariableDocumentState(parent))
        .hasValueSatisfying(
            state -> {
              assertThat(state.getKey()).isEqualTo(5L);
              assertThat(state.getRecord().getScopeKey()).isEqualTo(parent);
              assertThat(state.getRecord().getUpdateSemantics())
                  .isEqualTo(VariableDocumentUpdateSemantic.LOCAL);
              assertEquality(state.getRecord().getVariablesBuffer(), "{'a': 1}");
            });
    assertThat(variableState.findVariableDocumentState(child)).isEmpty();

    // when
    variableState.removeVariableDocumentState(parent);

    // then
    assertThat(variableState.findVariableDocumentState(parent)).isEmpty();
  }

  protected void declareScope(final long key) {
    declareScope(-1, key);
  }

  /**
   * Creates the element instance {@code key} in the flow scope {@code parentKey} (-1 for a process
   * instance), with the element instance path of the engine: the flow scope's path plus the key.
   */
  protected void declareScope(final long parentKey, final long key) {
    final ElementInstance parent = elementInstanceState.getInstance(parentKey);
    final List<List<Long>> path = new ArrayList<>();
    if (parentKey < 0) {
      path.add(List.of(key));
    } else {
      path.addAll(elementInstancePaths.get(parentKey));
      final List<Long> instancePath = new ArrayList<>(path.removeLast());
      instancePath.add(key);
      path.add(instancePath);
    }
    elementInstancePaths.put(key, path);

    final ProcessInstanceRecord record = new ProcessInstanceRecord();
    if (parentKey >= 0) {
      record.setFlowScopeKey(parentKey);
    }
    record.setElementInstancePath(path);
    elementInstanceState.newInstance(parent, key, record, ProcessInstanceIntent.ELEMENT_ACTIVATING);
  }

  protected long setVariableLocal(
      final long scopeKey, final DirectBuffer name, final DirectBuffer value) {
    final long key = KEY_GENERATOR.incrementAndGet();
    variableState.setVariableLocal(key, scopeKey, PROCESS_KEY, name, value);
    return key;
  }
}
