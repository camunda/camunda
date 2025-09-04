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
   * Creates a lightweight "pointer" entry for a variable. This associates the given {@code name}
   * with the {@code scopeKey} without storing a concrete value. Pointers are typically used as
   * placeholders or to track references to variables which will be resolved later.
   *
   * <p>This method is expected to be called only from an {@link
   * io.camunda.zeebe.engine.state.EventApplier} or from tests.
   *
   * @param recordKey the unique key identifying this variable pointer record
   * @param scopeKey the local scope of the variable pointer
   * @param name the name of the variable being pointed to (UTF-8 encoded in {@link DirectBuffer})
   */
  void createVariablePointer(final long recordKey, final long scopeKey, final DirectBuffer name);

  /**
   * Removes the variable with the given {@code name} from the specified {@code scopeKey}.
   *
   * <p>This deletes the variable entry itself; if there is an associated variable pointer, use
   * {@link #removeVariableVariablePointer(long)} to remove that as well.
   *
   * <p>This method is expected to be called only from an {@link
   * io.camunda.zeebe.engine.state.EventApplier} or from tests.
   *
   * @param scopeKey the local scope key from which the variable should be removed
   * @param name the name of the variable to remove (UTF-8 encoded in {@link DirectBuffer})
   */
  void removeVariable(final long scopeKey, final DirectBuffer name);

  /**
   * Removes a previously created variable pointer identified by the given {@code key}.
   *
   * <p>This does not delete the variable value itself, only the pointer entry. To remove the
   * variable value, use {@link #removeVariable(long, DirectBuffer)}.
   *
   * <p>This method is expected to be called only from an {@link
   * io.camunda.zeebe.engine.state.EventApplier} or from tests.
   *
   * @param key the unique key identifying the variable pointer record to remove
   */
  void removeVariableVariablePointer(final long key);

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
  void storeVariableDocumentState(long key, VariableDocumentRecord value);

  /**
   * Removes the variable document record from the state, associated with the provided scope key.
   *
   * <p>This method is expected to be called directly ONLY from an {@link
   * io.camunda.zeebe.engine.state.EventApplier} or from tests.
   *
   * @param scopeKey the key identifying the scope for which the variable document is stored
   */
  void removeVariableDocumentState(long scopeKey);
}
