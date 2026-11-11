/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.VariableDocumentState;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Persistence of the variable state: the variables by scope and name, the scope hierarchy and the
 * variable document states, without the domain rules of {@link VariableStateImpl} (which variables
 * a document contains, shadowing of parent variables, keeping a variable's metadata).
 *
 * <p>Besides the reads and writes of one scope, the store answers the lookups along a scope chain:
 * a scope and its ancestors, from the scope up to its root. The store resolves the chain, so it can
 * walk it scope by scope or read all scopes of the chain at once.
 *
 * <p>Unless documented otherwise, returned and visited objects may be backed by buffers that the
 * store reuses on its next read: callers must not keep them across another read of the store.
 */
public interface VariableStore {

  /**
   * @return the variable with the name in the scope, or null if the scope has none
   */
  @Nullable VariableInstance getVariable(
      long scopeKey, DirectBuffer name, int nameOffset, int nameLength);

  /**
   * @return the variable with the name in the nearest scope of the chain starting at the given
   *     scope, or null if no scope of the chain has one
   */
  @Nullable VariableInstance findVariable(
      long scopeKey, DirectBuffer name, int nameOffset, int nameLength);

  /**
   * Inserts the variable with the name in the scope, or overwrites the existing one.
   *
   * @param record the record of the event that sets the variable, if there is one; a store may take
   *     the process instance the variable belongs to from it
   */
  void putVariable(
      long scopeKey,
      DirectBuffer name,
      int nameOffset,
      int nameLength,
      VariableInstance variable,
      @Nullable VariableRecord record);

  /** Deletes all variables of the scope. */
  void deleteVariables(long scopeKey);

  /**
   * Visits the variables of the scope, ordered by name, until the completion condition is met.
   *
   * @param filter tested with the name of each variable; only matching variables are consumed
   * @param consumer receives the name and the variable
   * @param completionCondition tested after each visited variable; the visit stops once it is true
   * @return whether the completion condition is met
   */
  boolean visitLocalVariables(
      long scopeKey,
      Predicate<DirectBuffer> filter,
      BiConsumer<DirectBuffer, VariableInstance> consumer,
      BooleanSupplier completionCondition);

  /**
   * Like {@link #visitLocalVariables}, for each scope of the chain starting at the given scope,
   * from the nearest scope to the root, until the completion condition is met after a scope.
   *
   * @param names if not null, the only names the filter accepts; a store may use it to read only
   *     these variables
   */
  void visitVariables(
      long scopeKey,
      @Nullable Collection<DirectBuffer> names,
      Predicate<DirectBuffer> filter,
      BiConsumer<DirectBuffer, VariableInstance> consumer,
      BooleanSupplier completionCondition);

  /**
   * Reads ahead what {@link #visitVariables} will read for each of the scopes, see {@link
   * VariableState#prefetchVariables}. Does nothing unless the store benefits from it.
   *
   * @param names if not null, the only names the visits will accept
   */
  default void prefetchVariables(
      final Collection<Long> scopeKeys, final @Nullable Collection<DirectBuffer> names) {}

  /** Records the parent of a new scope; {@link VariableState#NO_PARENT} for a root scope. */
  void createScope(long childKey, long parentKey);

  /** Deletes the record of the scope's parent, if there is one. */
  void deleteScope(long scopeKey);

  /**
   * @return the parent of the scope, or {@link VariableState#NO_PARENT} if it has none or is
   *     unknown
   */
  long getParentScopeKey(long childScopeKey);

  /** Inserts the variable document state of the record's scope. */
  void insertDocumentState(long key, VariableDocumentRecord record);

  /** Deletes the variable document state of the scope, if there is one. */
  void deleteDocumentState(long scopeKey);

  @Nullable VariableDocumentState getDocumentState(long scopeKey);

  /**
   * @return whether the store holds neither variables nor scopes
   */
  boolean isEmpty();
}
