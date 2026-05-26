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
   * Updates the last-sent timestamp for a pending ask. Called by the retry scheduler when it
   * re-sends the ask.
   *
   * @param messageKey the key of the message that triggered the ask
   * @param processDefinitionKey the key of the process definition the ask targets
   * @param lastSentTime epoch millis when the ask was last sent
   */
  void updateLastSentTime(long messageKey, long processDefinitionKey, long lastSentTime);
}
