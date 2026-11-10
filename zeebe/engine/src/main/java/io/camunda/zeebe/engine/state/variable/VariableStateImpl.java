/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.engine.state.instance.VariableDocumentState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.collections.ObjectHashSet;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * The variable state, independent of where it is stored: collects the variables of a scope chain
 * into documents, where a variable of a scope hides the one with the same name in its ancestors,
 * and keeps the metadata of a variable that is set without metadata. The {@link VariableStore}
 * persists it and resolves the scope chains.
 */
public final class VariableStateImpl implements MutableVariableState {

  private final VariableStore store;

  private final MsgPackWriter writer = new MsgPackWriter();
  private final ExpandableArrayBuffer documentResultBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer resultView = new UnsafeBuffer(0, 0);

  private final VariableInstance newVariable = new VariableInstance();

  // collecting variables
  private final ObjectHashSet<DirectBuffer> collectedVariables = new ObjectHashSet<>();
  private final ObjectHashSet<DirectBuffer> variablesToCollect = new ObjectHashSet<>();

  public VariableStateImpl(final VariableStore store) {
    this.store = store;
  }

  @Override
  public void setVariableLocal(
      final long key,
      final long scopeKey,
      final long processDefinitionKey,
      final DirectBuffer name,
      final DirectBuffer value) {
    setVariableLocal(
        key, scopeKey, processDefinitionKey, name, 0, name.capacity(), value, 0, value.capacity());
  }

  @Override
  public void setVariableLocal(
      final long key,
      final long scopeKey,
      final long processDefinitionKey,
      final DirectBuffer name,
      final int nameOffset,
      final int nameLength,
      final DirectBuffer value,
      final int valueOffset,
      final int valueLength) {
    setVariableLocal(
        key,
        scopeKey,
        processDefinitionKey,
        name,
        nameOffset,
        nameLength,
        value,
        valueOffset,
        valueLength,
        null,
        null);
  }

  @Override
  public void setVariableLocal(
      final long key,
      final long scopeKey,
      final long processDefinitionKey,
      final DirectBuffer name,
      final DirectBuffer value,
      final VariableMetadataRecord metadata) {
    setVariableLocal(
        key,
        scopeKey,
        processDefinitionKey,
        name,
        0,
        name.capacity(),
        value,
        0,
        value.capacity(),
        metadata,
        null);
  }

  @Override
  public void setVariableLocal(final long key, final VariableRecord variable) {
    final DirectBuffer name = variable.getNameBuffer();
    final DirectBuffer value = variable.getValueBuffer();
    setVariableLocal(
        key,
        variable.getScopeKey(),
        variable.getProcessDefinitionKey(),
        name,
        0,
        name.capacity(),
        value,
        0,
        value.capacity(),
        variable.getMetadata(),
        variable);
  }

  private void setVariableLocal(
      final long key,
      final long scopeKey,
      final long processDefinitionKey,
      final DirectBuffer name,
      final int nameOffset,
      final int nameLength,
      final DirectBuffer value,
      final int valueOffset,
      final int valueLength,
      final @Nullable VariableMetadataRecord metadata,
      final @Nullable VariableRecord record) {
    newVariable.reset();
    newVariable.setValue(value, valueOffset, valueLength);
    newVariable.setKey(key);
    setVariableMetadata(scopeKey, name, nameOffset, nameLength, metadata);

    store.putVariable(scopeKey, name, nameOffset, nameLength, newVariable, record);
  }

  @Override
  public void createScope(final long childKey, final long parentKey) {
    store.createScope(childKey, parentKey);
  }

  @Override
  public void removeScope(final long scopeKey) {
    removeAllVariables(scopeKey);
    store.deleteScope(scopeKey);
  }

  @Override
  public void removeAllVariables(final long scopeKey) {
    store.deleteVariables(scopeKey);
  }

  @Override
  public DirectBuffer getVariableLocal(final long scopeKey, final DirectBuffer name) {
    final VariableInstance variable = store.getVariable(scopeKey, name, 0, name.capacity());

    if (variable != null) {
      return variable.getValue();
    } else {
      return null;
    }
  }

  /**
   * Find the variable with the given name. If the variable is not present in the given scope then
   * it looks in the parent scope and continues until it is found.
   *
   * @param scopeKey the key of the variable scope to start from
   * @param name the name of the variable
   * @return the value of the variable, or {@code null} if it is not present in the variable scope
   */
  @Override
  public DirectBuffer getVariable(final long scopeKey, final DirectBuffer name) {
    return getVariable(scopeKey, name, 0, name.capacity());
  }

  /**
   * Find the variable with the given name. If the variable is not present in the given scope then
   * it looks in the parent scope and continues until it is found.
   *
   * @param scopeKey the key of the variable scope to start from
   * @param name the buffer that contains the name of the variable
   * @param nameOffset the offset of name in the buffer
   * @param nameLength the length of the name in the buffer
   * @return the value of the variable, or {@code null} if it is not present in the variable scope
   */
  @Override
  public DirectBuffer getVariable(
      final long scopeKey, final DirectBuffer name, final int nameOffset, final int nameLength) {
    final VariableInstance variable = store.findVariable(scopeKey, name, nameOffset, nameLength);
    return variable != null ? variable.getValue() : null;
  }

  @Override
  public void prefetchVariables(
      final Collection<Long> scopeKeys, final Collection<DirectBuffer> names) {
    store.prefetchVariables(scopeKeys, names.isEmpty() ? null : names);
  }

  @Override
  public DirectBuffer getVariablesAsDocument(final long scopeKey) {

    collectedVariables.clear();
    writer.wrap(documentResultBuffer, 0);

    writer.reserveMapHeader();

    store.visitVariables(
        scopeKey,
        null,
        name -> !collectedVariables.contains(name),
        (name, value) -> {
          writer.writeString(name);
          writer.writeRaw(value.getValue());

          // must create a new name wrapper, because we keep them all in the hashset at the same
          // time
          final MutableDirectBuffer nameView = new UnsafeBuffer(name);
          collectedVariables.add(nameView);
        },
        () -> false);

    writer.writeReservedMapHeader(0, collectedVariables.size());

    resultView.wrap(documentResultBuffer, 0, writer.getOffset());
    return resultView;
  }

  @Override
  public DirectBuffer getVariablesAsDocument(
      final long scopeKey, final Collection<DirectBuffer> names) {

    variablesToCollect.clear();
    variablesToCollect.addAll(names);

    writer.wrap(documentResultBuffer, 0);

    writer.reserveMapHeader();

    final MutableInteger written = new MutableInteger(0);
    store.visitVariables(
        scopeKey,
        names,
        variablesToCollect::contains,
        (name, value) -> {
          writer.writeString(name);
          writer.writeRaw(value.getValue());

          variablesToCollect.remove(name);
          written.increment();
        },
        variablesToCollect::isEmpty);

    writer.writeReservedMapHeader(0, written.get());

    resultView.wrap(documentResultBuffer, 0, writer.getOffset());
    return resultView;
  }

  @Override
  public DirectBuffer getVariablesLocalAsDocument(final long scopeKey) {
    writer.wrap(documentResultBuffer, 0);
    writer.reserveMapHeader();

    final MutableInteger variableCount = new MutableInteger();
    store.visitLocalVariables(
        scopeKey,
        name -> true,
        (name, value) -> {
          writer.writeString(name);
          writer.writeRaw(value.getValue());

          variableCount.addAndGet(1);
        },
        () -> false);

    writer.writeReservedMapHeader(0, variableCount.get());

    resultView.wrap(documentResultBuffer, 0, writer.getOffset());
    return resultView;
  }

  @Override
  public boolean isEmpty() {
    return store.isEmpty();
  }

  @Override
  public List<Variable> getVariablesLocal(final long scopeKey) {
    final List<Variable> variables = new ArrayList<>();
    store.visitLocalVariables(
        scopeKey,
        name -> true,
        (name, variable) ->
            variables.add(
                new Variable(
                    variable.getKey(),
                    scopeKey,
                    BufferUtil.cloneBuffer(name),
                    BufferUtil.cloneBuffer(variable.getValue()))),
        () -> false);
    return variables;
  }

  @Override
  public VariableInstance getVariableInstanceLocal(final long scopeKey, final DirectBuffer name) {
    return store.getVariable(scopeKey, name, 0, name.capacity());
  }

  @Override
  public long getParentScopeKey(final long childScopeKey) {
    return store.getParentScopeKey(childScopeKey);
  }

  @Override
  public void storeVariableDocumentState(final long key, final VariableDocumentRecord record) {
    store.insertDocumentState(key, record);
  }

  @Override
  public void removeVariableDocumentState(final long scopeKey) {
    store.deleteDocumentState(scopeKey);
  }

  @Override
  public Optional<VariableDocumentState> findVariableDocumentState(final long scopeKey) {
    return Optional.ofNullable(store.getDocumentState(scopeKey));
  }

  /**
   * Sets the given metadata on {@link #newVariable}. Without metadata, the metadata already stored
   * for the variable is kept, so writers that predate metadata do not erase it.
   */
  private void setVariableMetadata(
      final long scopeKey,
      final DirectBuffer name,
      final int nameOffset,
      final int nameLength,
      final @Nullable VariableMetadataRecord metadata) {
    if (metadata != null) {
      newVariable.setMetadata(metadata);
      return;
    }

    final VariableInstance existing = store.getVariable(scopeKey, name, nameOffset, nameLength);
    if (existing != null) {
      newVariable.setMetadata(existing.getMetadata());
    }
  }
}
