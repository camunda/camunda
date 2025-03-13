/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import org.agrona.DirectBuffer;

public interface MutableVariableState extends VariableState {

  /**
   * Creates or updates the variable with {@code name} within the given scope with {@code scopeKey},
   * setting its value to the given {@code value}.
   *
   * <p>This method is expected to be called directly ONLY from an {@link
   * io.camunda.zeebe.engine.state.EventApplier} or from tests.
   *
   * @param key the variable key
   * @param scopeKey the local scope of the variable
   * @param processDefinitionKey the associated process key, mostly for monitoring purposes
   * @param name the name of the variable
   * @param value the value of the variable (MsgPack encoded)
   */
  void setVariableLocal(
      long key, long scopeKey, long processDefinitionKey, DirectBuffer name, DirectBuffer value);

  /**
   * Creates or updates the variable with {@code name} within the given scope with {@code scopeKey},
   * setting its value to the given {@code value}.
   *
   * <p>This method is expected to be called directly ONLY from an {@link
   * io.camunda.zeebe.engine.state.EventApplier} or from tests.
   *
   * @param key the variable key
   * @param scopeKey the local scope of the variable
   * @param processDefinitionKey the associated process key, mostly for monitoring purposes
   * @param name the name of the variable
   * @param nameOffset offset at which the name starts in the {@code name} buffer
   * @param nameLength length of the variable name in the {@code name} buffer
   * @param value the value of the variable (MsgPack encoded)
   * @param valueOffset offset at which the value starts in the {@code value} buffer
   * @param valueLength length of the variable value in the {@code value} buffer
   */
  void setVariableLocal(
      long key,
      long scopeKey,
      long processDefinitionKey,
      DirectBuffer name,
      int nameOffset,
      int nameLength,
      DirectBuffer value,
      int valueOffset,
      int valueLength);

  void createScope(long childKey, long parentKey);

  void removeScope(long scopeKey);

  void removeAllVariables(long scopeKey);

  /**
   * Stores the given variable document record in the state, associating it with the provided key.
   *
   * <p>This method is expected to be called directly ONLY from an {@link
   * io.camunda.zeebe.engine.state.EventApplier} or from tests.
   *
   * @param key the unique key identifying the variable document
   * @param value the variable document record containing the updated variables within a given scope
   */
  void storeVariableDocument(long key, VariableDocumentRecord value);
}
