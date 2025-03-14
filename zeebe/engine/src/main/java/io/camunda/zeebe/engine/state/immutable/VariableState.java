/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.instance.VariableDocumentState;
import io.camunda.zeebe.engine.state.variable.VariableInstance;
import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;

public interface VariableState {

  /** The value of the parent scope key for scope with no parents. */
  long NO_PARENT = -1;

  DirectBuffer getVariableLocal(long scopeKey, DirectBuffer name);

  DirectBuffer getVariable(long scopeKey, DirectBuffer name);

  DirectBuffer getVariable(long scopeKey, DirectBuffer name, int nameOffset, int nameLength);

  DirectBuffer getVariablesAsDocument(long scopeKey);

  DirectBuffer getVariablesAsDocument(long scopeKey, Collection<DirectBuffer> names);

  DirectBuffer getVariablesLocalAsDocument(long scopeKey);

  boolean isEmpty();

  /**
   * Returns a list of all variables at the given scope key.
   *
   * <p>This method differs from most other methods on this interface in that it does not traverse
   * the scope hierarchy. It only returns variables that are directly stored at the given scope key.
   *
   * @param scopeKey the scope key to get the variables for
   * @return a list of all variables at the given scope key
   */
  List<Variable> getVariablesLocal(long scopeKey);

  VariableInstance getVariableInstanceLocal(long scopeKey, DirectBuffer name);

  /**
   * @return returns the parent scope key of the given {@code childScopeKey}, or {@link
   *     VariableState#NO_PARENT}
   */
  long getParentScopeKey(long childScopeKey);

  /**
   * Retrieves the persisted {@link VariableDocumentState} for the given scope key.
   *
   * <p>This method provides access to the variable document key and its associated variable
   * document record within the specified scope. It's particularly useful in scenarios where the
   * original variable update request needs to be referenced, such as during the finalization of a
   * user task update transition triggered by a {@code VariableDocumentIntent#UPDATE} command.
   *
   * @param scopeKey the scope key for which to retrieve the variable document
   * @return the {@link VariableDocumentState} if found, otherwise {@code null}
   */
  VariableDocumentState getVariableDocumentState(long scopeKey);

  /** Data wrapper for a variable. */
  record Variable(long key, long scopeKey, DirectBuffer name, DirectBuffer value) {}
}
