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
import java.util.function.BiConsumer;

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
  ActorFuture<List<Namespace>> list(NamespaceScope scope);

  /** Deletes the namespace with all its keys, unconditionally. */
  ActorFuture<Void> deleteNamespace(Namespace namespace);

  /**
   * Registers a listener for namespaces of the scope being created or deleted. It reports
   * namespaces only, never their keys. Registration does not report existing namespaces: register
   * first, then call {@link #list(NamespaceScope)}. The listener is called on the given executor,
   * one event at a time and in commit order.
   *
   * @throws ClusterMetadataStoreException.ViewStalledException if this broker's view can no longer
   *     follow the store; nothing is registered
   * @throws ClusterMetadataStoreException.StoreClosedException if the store is closed; nothing is
   *     registered
   */
  Subscription watchNamespace(
      NamespaceScope scope, BiConsumer<Namespace, NamespaceEvent> listener, Executor executor);

  /** Whose data a namespace holds. */
  enum NamespaceScope {
    /** The whole cluster; the namespace name is a fixed name chosen by its consumer. */
    CLUSTER,
    /** One physical tenant; the namespace name is the physical tenant id. */
    PHYSICAL_TENANT
  }

  /** What happened to a namespace, as {@link #watchNamespace} delivers it. */
  enum NamespaceEvent {
    CREATED,
    DELETED
  }
}
