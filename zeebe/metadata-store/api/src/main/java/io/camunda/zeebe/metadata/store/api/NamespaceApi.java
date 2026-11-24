/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Creates, deletes, lists and follows namespaces. Handed to the components that manage namespaces
 * and to the ones that follow them; components that only read and write keys use {@link
 * ClusterMetadataStore}.
 */
public interface NamespaceApi {

  /**
   * Creates the namespace. If it exists, the returned future fails with {@link
   * ClusterMetadataStoreException.NamespaceAlreadyExistsException}.
   */
  ActorFuture<Void> createNamespace(Namespace namespace);

  ActorFuture<Boolean> exists(Namespace namespace);

  /** Every namespace of the scope, for example one per physical tenant. */
  ActorFuture<List<Namespace>> list(NamespaceScope scope);

  /** Deletes the namespace with all its keys, unconditionally. */
  ActorFuture<Void> deleteNamespace(Namespace namespace);

  /**
   * Registers a listener for namespaces of the scope being created or deleted. It reports
   * namespaces only, never their keys. Registration does not report existing namespaces: register
   * first, then call {@link #list(NamespaceScope)}. The listener is called on the given executor,
   * one event at a time and in commit order. Like {@link ClusterMetadataStore#watch}, it returns
   * its handle at once and throws the errors known when it is called.
   *
   * @throws ClusterMetadataStoreException.ViewStalledException if this broker's view can no longer
   *     follow the store; nothing is registered
   * @throws ClusterMetadataStoreException.StoreClosedException if the store is closed; nothing is
   *     registered
   */
  Subscription watchNamespace(
      NamespaceScope scope, WatchListener<NamespaceChange> listener, Executor executor);
}
