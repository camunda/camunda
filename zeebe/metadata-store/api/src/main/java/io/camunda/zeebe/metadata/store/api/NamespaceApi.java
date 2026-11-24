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
   * Creates the namespace. Fails with {@link
   * ClusterMetadataStoreException.NamespaceAlreadyExistsException} if it exists.
   */
  ActorFuture<Void> createNamespace(Namespace namespace);

  ActorFuture<Boolean> exists(Namespace namespace);

  /** Every namespace of the scope, for example one per physical tenant. */
  ActorFuture<List<Namespace>> list(Namespace.Scope scope);

  /** Deletes the namespace with all its keys, unconditionally. */
  ActorFuture<Void> deleteNamespace(Namespace namespace);

  /**
   * Registers a listener for namespaces of the scope being created or deleted. It reports
   * namespaces only, never their keys. Registration does not report existing namespaces: once the
   * future completes, call {@link #list(Namespace.Scope)}.
   *
   * <p>Fails, with nothing registered, with {@link
   * ClusterMetadataStoreException.ViewStalledException} if this broker's view can no longer follow
   * the store, or {@link ClusterMetadataStoreException.StoreClosedException} if the store is
   * closed.
   */
  ActorFuture<Subscription> watchNamespace(
      Namespace.Scope scope, Subscription.Listener<NamespaceEvent> listener, Executor executor);
}
