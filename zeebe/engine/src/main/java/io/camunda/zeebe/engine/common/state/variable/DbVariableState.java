/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.variable;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.common.state.instance.ParentScopeKey;
import io.camunda.zeebe.engine.common.state.instance.VariableDocumentState;
import io.camunda.zeebe.engine.common.state.mutable.MutableVariableState;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.collections.ObjectHashSet;
import org.agrona.concurrent.UnsafeBuffer;

public class DbVariableState implements MutableVariableState {

  private final MsgPackWriter writer = new MsgPackWriter();
  private final ExpandableArrayBuffer documentResultBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer resultView = new UnsafeBuffer(0, 0);

  // (child scope key) => (parent scope key)
  private final ColumnFamily<DbLong, ParentScopeKey> childParentColumnFamily;
  private final DbLong childKey;
  private final ParentScopeKey parentKey = new ParentScopeKey();

  // (scope key, variable name) => (variable value)
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, VariableInstance>
      variablesColumnFamily;
  private final DbCompositeKey<DbLong, DbString> scopeKeyVariableNameKey;
  private final DbLong scopeKey;
  private final DbString variableName;

  // (scope key) => (variable document state)
  // we need two separate wrapper to not interfere with get and put
  // see https://github.com/zeebe-io/zeebe/issues/1914
  private final VariableDocumentState variableDocumentStateToRead = new VariableDocumentState();
  private final VariableDocumentState variableDocumentStateToWrite = new VariableDocumentState();
  private final ColumnFamily<DbLong, VariableDocumentState>
      variableDocumentStateByScopeKeyColumnFamily;

  private final VariableInstance newVariable = new VariableInstance();
  private final DirectBuffer variableNameView = new UnsafeBuffer(0, 0);

  // collecting variables
  private final ObjectHashSet<DirectBuffer> collectedVariables = new ObjectHashSet<>();
  private final ObjectHashSet<DirectBuffer> variablesToCollect = new ObjectHashSet<>();

  public DbVariableState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    childKey = new DbLong();
    childParentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_INSTANCE_CHILD_PARENT,
            transactionContext,
            childKey,
            parentKey);

    scopeKey = new DbLong();
    variableName = new DbString();
    scopeKeyVariableNameKey = new DbCompositeKey<>(scopeKey, variableName);
    variablesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.VARIABLES,
            transactionContext,
            scopeKeyVariableNameKey,
            new VariableInstance());

    variableDocumentStateByScopeKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.VARIABLE_DOCUMENT_STATE_BY_SCOPE_KEY,
            transactionContext,
            scopeKey,
            variableDocumentStateToRead);
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

    newVariable.reset();
    newVariable.setValue(value, valueOffset, valueLength);
    newVariable.setKey(key);

    this.scopeKey.wrapLong(scopeKey);
    variableNameView.wrap(name, nameOffset, nameLength);
    variableName.wrapBuffer(variableNameView);

    variablesColumnFamily.upsert(scopeKeyVariableNameKey, newVariable);
  }

  @Override
  public void createScope(final long childKey, final long parentKey) {
    this.childKey.wrapLong(childKey);
    this.parentKey.set(parentKey);

    childParentColumnFamily.insert(this.childKey, this.parentKey);
  }

  @Override
  public void removeScope(final long scopeKey) {
    this.scopeKey.wrapLong(scopeKey);

    removeAllVariables(scopeKey);

    childKey.wrapLong(scopeKey);
    // TODO: Could be deleteExisting except for tests
    childParentColumnFamily.deleteIfExists(childKey);
  }

  @Override
  public void removeAllVariables(final long scopeKey) {
    visitVariablesLocal(
        scopeKey,
        dbString -> true,
        (dbString, variable1) -> variablesColumnFamily.deleteExisting(scopeKeyVariableNameKey),
        () -> false);
  }

  @Override
  public DirectBuffer getVariableLocal(final long scopeKey, final DirectBuffer name) {
    final VariableInstance variable = getVariableLocal(scopeKey, name, 0, name.capacity());

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

    long currentScopeKey = scopeKey;
    do {
      final VariableInstance variable =
          getVariableLocal(currentScopeKey, name, nameOffset, nameLength);

      if (variable != null) {
        return variable.getValue();
      }

      currentScopeKey = getParentScopeKey(currentScopeKey);
    } while (currentScopeKey >= 0);

    return null;
  }

  @Override
  public DirectBuffer getVariablesAsDocument(final long scopeKey) {

    collectedVariables.clear();
    writer.wrap(documentResultBuffer, 0);

    writer.reserveMapHeader();

    visitVariables(
        scopeKey,
        name -> !collectedVariables.contains(name.getBuffer()),
        (name, value) -> {
          final DirectBuffer variableNameBuffer = name.getBuffer();
          writer.writeString(variableNameBuffer);
          writer.writeRaw(value.getValue());

          // must create a new name wrapper, because we keep them all in the hashset at the same
          // time
          final MutableDirectBuffer nameView = new UnsafeBuffer(variableNameBuffer);
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

    visitVariables(
        scopeKey,
        name -> variablesToCollect.contains(name.getBuffer()),
        (name, value) -> {
          writer.writeString(name.getBuffer());
          writer.writeRaw(value.getValue());

          variablesToCollect.remove(name.getBuffer());
        },
        variablesToCollect::isEmpty);

    writer.writeReservedMapHeader(0, names.size() - variablesToCollect.size());

    resultView.wrap(documentResultBuffer, 0, writer.getOffset());
    return resultView;
  }

  @Override
  public DirectBuffer getVariablesLocalAsDocument(final long scopeKey) {
    writer.wrap(documentResultBuffer, 0);
    writer.reserveMapHeader();

    final MutableInteger variableCount = new MutableInteger();
    visitVariablesLocal(
        scopeKey,
        name -> true,
        (name, value) -> {
          writer.writeString(name.getBuffer());
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
    return variablesColumnFamily.isEmpty() && childParentColumnFamily.isEmpty();
  }

  @Override
  public List<Variable> getVariablesLocal(final long scopeKey) {
    final List<Variable> variables = new ArrayList<>();
    visitVariablesLocal(
        scopeKey,
        name -> true,
        (name, variable) ->
            variables.add(
                new Variable(
                    variable.getKey(),
                    scopeKey,
                    BufferUtil.cloneBuffer(name.getBuffer()),
                    BufferUtil.cloneBuffer(variable.getValue()))),
        () -> false);
    return variables;
  }

  @Override
  public VariableInstance getVariableInstanceLocal(final long scopeKey, final DirectBuffer name) {
    return getVariableLocal(scopeKey, name, 0, name.capacity());
  }

  @Override
  public long getParentScopeKey(final long childScopeKey) {
    childKey.wrapLong(childScopeKey);

    final ParentScopeKey parentScopeKey = childParentColumnFamily.get(childKey);
    return parentScopeKey != null ? parentScopeKey.get() : NO_PARENT;
  }

  @Override
  public void storeVariableDocumentState(final long key, final VariableDocumentRecord record) {
    scopeKey.wrapLong(record.getScopeKey());
    variableDocumentStateToWrite.setKey(key).setRecord(record);
    variableDocumentStateByScopeKeyColumnFamily.insert(scopeKey, variableDocumentStateToWrite);
  }

  @Override
  public void removeVariableDocumentState(final long scopeKey) {
    this.scopeKey.wrapLong(scopeKey);
    variableDocumentStateByScopeKeyColumnFamily.deleteIfExists(this.scopeKey);
  }

  @Override
  public Optional<VariableDocumentState> findVariableDocumentState(final long scopeKey) {
    this.scopeKey.wrapLong(scopeKey);
    return Optional.ofNullable(variableDocumentStateByScopeKeyColumnFamily.get(this.scopeKey));
  }

  private VariableInstance getVariableLocal(
      final long scopeKey, final DirectBuffer name, final int nameOffset, final int nameLength) {
    this.scopeKey.wrapLong(scopeKey);
    variableNameView.wrap(name, nameOffset, nameLength);
    variableName.wrapBuffer(variableNameView);

    return variablesColumnFamily.get(scopeKeyVariableNameKey);
  }

  /**
   * Like {@link #visitVariablesLocal(long, Predicate, BiConsumer, BooleanSupplier)} but walks up
   * the scope hierarchy.
   */
  private void visitVariables(
      final long scopeKey,
      final Predicate<DbString> filter,
      final BiConsumer<DbString, VariableInstance> variableConsumer,
      final BooleanSupplier completionCondition) {
    long currentScope = scopeKey;

    boolean completed;
    do {
      completed = visitVariablesLocal(currentScope, filter, variableConsumer, completionCondition);

      currentScope = getParentScopeKey(currentScope);

    } while (!completed && currentScope >= 0);
  }

  /**
   * Provides all variables of a scope to the given consumer until a condition is met.
   *
   * @param variableFilter evaluated with the name of each variable; the variable is consumed only
   *     if the filter returns true
   * @param variableConsumer a consumer that receives variable name and value
   * @param completionCondition evaluated after every consumption; if true, consumption stops.
   * @return true if the completion condition was met
   */
  private boolean visitVariablesLocal(
      final long scopeKey,
      final Predicate<DbString> variableFilter,
      final BiConsumer<DbString, VariableInstance> variableConsumer,
      final BooleanSupplier completionCondition) {
    this.scopeKey.wrapLong(scopeKey);

    variablesColumnFamily.whileEqualPrefix(
        this.scopeKey,
        (compositeKey, variable) -> {
          final DbString name = compositeKey.second();

          if (variableFilter.test(name)) {
            variableConsumer.accept(name, variable);
          }

          return !completionCondition.getAsBoolean();
        });
    return false;
  }
}
