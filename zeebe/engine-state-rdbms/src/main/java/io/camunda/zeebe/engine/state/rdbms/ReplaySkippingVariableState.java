/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.instance.VariableDocumentState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.state.variable.VariableInstance;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Leaves the variable rows alone while the replay applies events whose changes the database already
 * contains (see {@link JdbcTransactionContext#isReplayingCommittedChanges()}): the mutators do
 * nothing, and the reads see the rows in the database's later state. Otherwise, every call is
 * delegated.
 */
@NullMarked
final class ReplaySkippingVariableState implements MutableVariableState {

  private final MutableVariableState delegate;
  private final JdbcTransactionContext context;

  ReplaySkippingVariableState(
      final MutableVariableState delegate, final JdbcTransactionContext context) {
    this.delegate = delegate;
    this.context = context;
  }

  private boolean skip() {
    return context.isReplayingCommittedChanges();
  }

  @Override
  public void setVariableLocal(
      final long key,
      final long scopeKey,
      final long processDefinitionKey,
      final DirectBuffer name,
      final DirectBuffer value) {
    if (!skip()) {
      delegate.setVariableLocal(key, scopeKey, processDefinitionKey, name, value);
    }
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
    if (!skip()) {
      delegate.setVariableLocal(
          key,
          scopeKey,
          processDefinitionKey,
          name,
          nameOffset,
          nameLength,
          value,
          valueOffset,
          valueLength);
    }
  }

  @Override
  public void setVariableLocal(
      final long key,
      final long scopeKey,
      final long processDefinitionKey,
      final DirectBuffer name,
      final DirectBuffer value,
      final VariableMetadataRecord metadata) {
    if (!skip()) {
      delegate.setVariableLocal(key, scopeKey, processDefinitionKey, name, value, metadata);
    }
  }

  @Override
  public void setVariableLocal(final long key, final VariableRecord variable) {
    if (!skip()) {
      delegate.setVariableLocal(key, variable);
    }
  }

  @Override
  public void createScope(final long childKey, final long parentKey) {
    if (!skip()) {
      delegate.createScope(childKey, parentKey);
    }
  }

  @Override
  public void removeScope(final long scopeKey) {
    if (!skip()) {
      delegate.removeScope(scopeKey);
    }
  }

  @Override
  public void removeAllVariables(final long scopeKey) {
    if (!skip()) {
      delegate.removeAllVariables(scopeKey);
    }
  }

  @Override
  public void storeVariableDocumentState(final long key, final VariableDocumentRecord value) {
    if (!skip()) {
      delegate.storeVariableDocumentState(key, value);
    }
  }

  @Override
  public void removeVariableDocumentState(final long scopeKey) {
    if (!skip()) {
      delegate.removeVariableDocumentState(scopeKey);
    }
  }

  @Override
  public @Nullable DirectBuffer getVariableLocal(final long scopeKey, final DirectBuffer name) {
    return delegate.getVariableLocal(scopeKey, name);
  }

  @Override
  public @Nullable DirectBuffer getVariable(final long scopeKey, final DirectBuffer name) {
    return delegate.getVariable(scopeKey, name);
  }

  @Override
  public @Nullable DirectBuffer getVariable(
      final long scopeKey, final DirectBuffer name, final int nameOffset, final int nameLength) {
    return delegate.getVariable(scopeKey, name, nameOffset, nameLength);
  }

  @Override
  public DirectBuffer getVariablesAsDocument(final long scopeKey) {
    return delegate.getVariablesAsDocument(scopeKey);
  }

  @Override
  public DirectBuffer getVariablesAsDocument(
      final long scopeKey, final Collection<DirectBuffer> names) {
    return delegate.getVariablesAsDocument(scopeKey, names);
  }

  @Override
  public DirectBuffer getVariablesLocalAsDocument(final long scopeKey) {
    return delegate.getVariablesLocalAsDocument(scopeKey);
  }

  @Override
  public void prefetchVariables(
      final Collection<Long> scopeKeys, final Collection<DirectBuffer> names) {
    delegate.prefetchVariables(scopeKeys, names);
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public List<Variable> getVariablesLocal(final long scopeKey) {
    return delegate.getVariablesLocal(scopeKey);
  }

  @Override
  public @Nullable VariableInstance getVariableInstanceLocal(
      final long scopeKey, final DirectBuffer name) {
    return delegate.getVariableInstanceLocal(scopeKey, name);
  }

  @Override
  public long getParentScopeKey(final long childScopeKey) {
    return delegate.getParentScopeKey(childScopeKey);
  }

  @Override
  public Optional<VariableDocumentState> findVariableDocumentState(final long scopeKey) {
    return delegate.findVariableDocumentState(scopeKey);
  }
}
