/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import io.camunda.zeebe.metadata.store.api.Subscription.CloseReason;

/**
 * Receives the events of one watch, on the executor given with it, one call at a time and in commit
 * order. A lambda gives {@link #onEvent}; a consumer that has to learn when the store ends the
 * watch implements {@link #onClosed} too.
 *
 * @param <E> the event type: {@link KeyEvent} for a key watch, {@link NamespaceChange} for a
 *     namespace watch
 */
@FunctionalInterface
public interface WatchListener<E> {

  /**
   * Called once for each change. A listener that throws is not called again for that event;
   * delivery goes on with the next one.
   */
  void onEvent(E event);

  /**
   * Called once, after the last event, when the store ends the watch, with the reason. It is not
   * called when the consumer closes the {@link Subscription} itself. This is how a consumer that
   * only watches learns that it has to register again.
   */
  default void onClosed(final CloseReason reason) {}
}
