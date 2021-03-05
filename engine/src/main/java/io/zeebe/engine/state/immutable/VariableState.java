/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.engine.state.variable.VariableInstance;
import java.util.Collection;
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

  DirectBuffer getTemporaryVariables(long scopeKey);

  boolean isEmpty();

  VariableInstance getVariableInstanceLocal(long scopeKey, DirectBuffer name);

  /**
   * @return returns the parent scope key of the given {@code childScopeKey}, or {@link
   *     VariableState#NO_PARENT}
   */
  long getParentScopeKey(long childScopeKey);
}
