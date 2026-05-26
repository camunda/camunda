/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceAsk;

/**
 * Read-only view of the pending cross-partition message-start ask state on {@code P_K}. An entry
 * exists for each outstanding ask (REQUEST sent, no reply yet). The entry is removed when any of
 * the three reply intents ({@code STARTED}, {@code UNIQUENESS_REJECTED}, {@code
 * NO_SUBSCRIPTION_REJECTED}) is applied locally.
 *
 * <p>Each entry is keyed by {@code (messageKey, processDefinitionKey)}; the pair uniquely
 * identifies an ask from this partition because a single message can only target one process
 * definition per message-start-event subscription.
 */
public interface MessageStartProcessInstanceAskState {

  /**
   * Returns the persisted ask for the given key, or {@code null} if no ask is pending.
   *
   * @param messageKey the key of the message that triggered the ask
   * @param processDefinitionKey the key of the process definition the ask targets
   * @return the ask, or {@code null} if none exists
   */
  MessageStartProcessInstanceAsk get(long messageKey, long processDefinitionKey);

  /**
   * Visits all persisted asks.
   *
   * @param visitor the visitor to call for each ask
   */
  void forEach(AskVisitor visitor);

  /**
   * Returns {@code true} if there are pending asks whose last-sent timestamp suggests they are
   * ready for retry (i.e., past the retry deadline).
   *
   * @param deadline epoch millis; returns true if any pending ask has {@code lastSentTime <
   *     deadline}
   * @return {@code true} if at least one ask is past its retry deadline
   */
  boolean hasPendingAsksPastDeadline(long deadline);

  /**
   * Returns all pending asks whose last-sent timestamp is before the given deadline; intended for
   * the scheduled retry loop.
   *
   * @param deadline epoch millis
   * @return an iterable of pending asks past their deadline
   */
  Iterable<MessageStartProcessInstanceAsk> getPendingAsksPastDeadline(long deadline);

  @FunctionalInterface
  interface AskVisitor {
    void visit(long messageKey, long processDefinitionKey, MessageStartProcessInstanceAsk ask);
  }
}
