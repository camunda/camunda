/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ParentScopeKey;
import io.camunda.zeebe.engine.state.instance.VariableDocumentState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Stores the variable state in RocksDB column families. The scope hierarchy is a map of child to
 * parent scope, and the lookups along a scope chain walk it scope by scope, stopping as soon as
 * they have their result.
 */
public final class DbVariableStore implements VariableStore {

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

  private final DirectBuffer variableNameView = new UnsafeBuffer(0, 0);

  public DbVariableStore(
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
  public @Nullable VariableInstance getVariable(
      final long scopeKey, final DirectBuffer name, final int nameOffset, final int nameLength) {
    this.scopeKey.wrapLong(scopeKey);
    variableNameView.wrap(name, nameOffset, nameLength);
    variableName.wrapBuffer(variableNameView);

    return variablesColumnFamily.get(scopeKeyVariableNameKey);
  }

  @Override
  public @Nullable VariableInstance findVariable(
      final long scopeKey, final DirectBuffer name, final int nameOffset, final int nameLength) {
    long currentScopeKey = scopeKey;
    do {
      final VariableInstance variable = getVariable(currentScopeKey, name, nameOffset, nameLength);

      if (variable != null) {
        return variable;
      }

      currentScopeKey = getParentScopeKey(currentScopeKey);
    } while (currentScopeKey >= 0);

    return null;
  }

  /** Doesn't store the process instance of a variable, so it ignores the record. */
  @Override
  public void putVariable(
      final long scopeKey,
      final DirectBuffer name,
      final int nameOffset,
      final int nameLength,
      final VariableInstance variable,
      final @Nullable VariableRecord record) {
    this.scopeKey.wrapLong(scopeKey);
    variableNameView.wrap(name, nameOffset, nameLength);
    variableName.wrapBuffer(variableNameView);

    variablesColumnFamily.upsert(scopeKeyVariableNameKey, variable);
  }

  @Override
  public void deleteVariables(final long scopeKey) {
    visitLocalVariables(
        scopeKey,
        name -> true,
        (name, variable) -> variablesColumnFamily.deleteExisting(scopeKeyVariableNameKey),
        () -> false);
  }

  /**
   * Provides all variables of a scope to the given consumer until a condition is met. The name is
   * the column family key's, so a consumer can delete the visited variable through it.
   */
  @Override
  public boolean visitLocalVariables(
      final long scopeKey,
      final Predicate<DirectBuffer> filter,
      final BiConsumer<DirectBuffer, VariableInstance> consumer,
      final BooleanSupplier completionCondition) {
    this.scopeKey.wrapLong(scopeKey);

    variablesColumnFamily.whileEqualPrefix(
        this.scopeKey,
        (compositeKey, variable) -> {
          final DirectBuffer name = compositeKey.second().getBuffer();

          if (filter.test(name)) {
            consumer.accept(name, variable);
          }

          return !completionCondition.getAsBoolean();
        });
    return completionCondition.getAsBoolean();
  }

  /** Walks up the scope hierarchy, scope by scope. Doesn't need the names. */
  @Override
  public void visitVariables(
      final long scopeKey,
      final @Nullable Collection<DirectBuffer> names,
      final Predicate<DirectBuffer> filter,
      final BiConsumer<DirectBuffer, VariableInstance> consumer,
      final BooleanSupplier completionCondition) {
    long currentScope = scopeKey;

    boolean completed;
    do {
      completed = visitLocalVariables(currentScope, filter, consumer, completionCondition);

      currentScope = getParentScopeKey(currentScope);

    } while (!completed && currentScope >= 0);
  }

  @Override
  public void createScope(final long childKey, final long parentKey) {
    this.childKey.wrapLong(childKey);
    this.parentKey.set(parentKey);

    childParentColumnFamily.insert(this.childKey, this.parentKey);
  }

  @Override
  public void deleteScope(final long scopeKey) {
    childKey.wrapLong(scopeKey);
    // TODO: Could be deleteExisting except for tests
    childParentColumnFamily.deleteIfExists(childKey);
  }

  @Override
  public long getParentScopeKey(final long childScopeKey) {
    childKey.wrapLong(childScopeKey);

    final ParentScopeKey parentScopeKey = childParentColumnFamily.get(childKey);
    return parentScopeKey != null ? parentScopeKey.get() : VariableState.NO_PARENT;
  }

  @Override
  public void insertDocumentState(final long key, final VariableDocumentRecord record) {
    scopeKey.wrapLong(record.getScopeKey());
    variableDocumentStateToWrite.setKey(key).setRecord(record);
    variableDocumentStateByScopeKeyColumnFamily.insert(scopeKey, variableDocumentStateToWrite);
  }

  @Override
  public void deleteDocumentState(final long scopeKey) {
    this.scopeKey.wrapLong(scopeKey);
    variableDocumentStateByScopeKeyColumnFamily.deleteIfExists(this.scopeKey);
  }

  @Override
  public @Nullable VariableDocumentState getDocumentState(final long scopeKey) {
    this.scopeKey.wrapLong(scopeKey);
    return variableDocumentStateByScopeKeyColumnFamily.get(this.scopeKey);
  }

  @Override
  public boolean isEmpty() {
    return variablesColumnFamily.isEmpty() && childParentColumnFamily.isEmpty();
  }
}
