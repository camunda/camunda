/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import java.util.function.Consumer;

/**
 * A registered listener, returned by {@link ClusterMetadataStore#watch} and {@link
 * NamespaceApi#watchNamespace}. Closing it removes the listener; closing it again does nothing.
 */
public interface Subscription extends AutoCloseable {

  @Override
  void close();

  /**
   * Registers a callback for the case that the store ends the subscription. It is called once, with
   * the reason, on the executor of the subscription and after its last event. It is not called when
   * the consumer closes the subscription itself. If the store has already ended the subscription,
   * the callback is scheduled on that executor after any earlier queued events.
   */
  void onClosed(Consumer<CloseReason> callback);

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
