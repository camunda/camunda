/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.state;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceNotFoundException;
import io.camunda.zeebe.metadata.store.api.KeyValue;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.NamespaceScope;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The whole store at one applied position: one immutable value, replaced after each applied entry.
 * Reads hand out immutable values, so a caller cannot change a published state.
 *
 * @param appliedPosition the position of the last applied entry
 * @param namespaces every namespace that exists, with its state
 */
public record StoreState(long appliedPosition, Map<Namespace, NamespaceState> namespaces) {

  private static final StoreState EMPTY = new StoreState(0, Map.of());

  public StoreState {
    namespaces = Map.copyOf(namespaces);
  }

  /** Returns the state of a store that has applied nothing. */
  public static StoreState empty() {
    return EMPTY;
  }

  public boolean exists(final Namespace namespace) {
    return namespaces.containsKey(namespace);
  }

  /** Returns every namespace of the scope, sorted by name. */
  public List<Namespace> namespaces(final NamespaceScope scope) {
    return namespaces.keySet().stream()
        .filter(namespace -> namespace.scope() == scope)
        .sorted(Comparator.comparing(Namespace::name))
        .toList();
  }

  /**
   * Returns the pair stored under exactly this key.
   *
   * @throws InvalidArgumentException if the key is empty; a namespace is read with {@link #list}
   * @throws NamespaceNotFoundException if the namespace of the key does not exist
   */
  public Optional<KeyValue> get(final StoreKey key) {
    if (key.isEmpty()) {
      throw new InvalidArgumentException(
          "Expected a non-empty key to get, but got the empty key of namespace '%s'"
              .formatted(key.namespace()));
    }
    return Optional.ofNullable(requireNamespace(key.namespace()).get(key.key()));
  }

  /**
   * Returns the pairs stored at or under the prefix, sorted by key.
   *
   * @throws NamespaceNotFoundException if the namespace of the prefix does not exist
   */
  public List<KeyValue> list(final StoreKey prefix) {
    return List.copyOf(requireNamespace(prefix.namespace()).atOrUnder(prefix.key()).values());
  }

  /**
   * Returns the state of the namespace.
   *
   * @throws NamespaceNotFoundException if it does not exist
   */
  public NamespaceState requireNamespace(final Namespace namespace) {
    final var state = namespaces.get(namespace);
    if (state == null) {
      throw new NamespaceNotFoundException(
          "Expected namespace '%s' to exist, but it does not".formatted(namespace));
    }
    return state;
  }
}
