/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceAsk;

/**
 * Mutable view of the pending cross-partition message-start ask state on {@code P_K}. The writer
 * that calls {@link #put} and the reply applier that calls {@link #remove} are attached in later
 * commits; this commit lands the interface and its implementation so the data shape is reviewable
 * in isolation.
 */
public interface MutableMessageStartProcessInstanceAskState
    extends MessageStartProcessInstanceAskState {

  /**
   * Persists a pending ask. If an ask for the same key already exists, it is overwritten.
   *
   * @param ask the ask to persist (values are deep-copied)
   */
  void put(MessageStartProcessInstanceAsk ask);

  /**
   * Removes a pending ask. Called when any of the three reply intents is applied on {@code P_K}.
   *
   * @param messageKey the key of the message that triggered the ask
   * @param processDefinitionKey the key of the process definition the ask targets
   */
  void remove(long messageKey, long processDefinitionKey);

  /**
   * Removes all pending asks for the given {@code messageKey}, regardless of process definition.
   * Called from the message-expire applier so that retries never outlive the buffered message they
   * refer to: when the message TTLs off on {@code P_K}, the dedup row on {@code P_B} (whose
   * deletion deadline equals the message's own deadline) is also already expired and will be swept,
   * so any retry would create a duplicate process instance. Removing the pending-ask here ensures
   * the retry is never emitted.
   *
   * @param messageKey the key of the expired buffered message
   */
  void removeAllByMessageKey(long messageKey);
}
