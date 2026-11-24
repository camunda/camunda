/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

/**
 * A registered {@link Listener}, returned by {@link ClusterMetadataStore#watch} and {@link
 * NamespaceApi#watchNamespace}. Closing it removes the listener; closing it again does nothing.
 */
public interface Subscription extends AutoCloseable {

  @Override
  void close();

  /**
   * Receives the events of a watch, on the executor given to the watch, one at a time and in commit
   * order.
   *
   * @param <E> the kind of event
   */
  @FunctionalInterface
  interface Listener<E> {

    void onEvent(E event);

    /**
     * Called once if the store ends the subscription, with the reason and after its last event. It
     * is not called when the consumer closes the subscription itself.
     */
    default void onClosed(final CloseReason reason) {}
  }

  /**
   * Why the store ended a subscription. New kinds may be added; a consumer that switches over them
   * keeps a default branch.
   */
  enum CloseReason {
    /** The namespace of the watched key was deleted. */
    NAMESPACE_DELETED,
    /** This broker's view can no longer follow the store; no further events will come. */
    VIEW_STALLED,
    /** The store was closed, for example because the broker shuts down. */
    STORE_CLOSED
  }
}
