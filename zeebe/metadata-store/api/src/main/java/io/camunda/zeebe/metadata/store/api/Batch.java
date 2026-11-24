/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.metadata.store.api.Write.Check;
import io.camunda.zeebe.metadata.store.api.Write.CreateNamespace;
import io.camunda.zeebe.metadata.store.api.Write.Delete;
import io.camunda.zeebe.metadata.store.api.Write.DeletePrefix;
import io.camunda.zeebe.metadata.store.api.Write.Put;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Writes that {@link ClusterMetadataStore#batch(Batch)} applies as one write, all or nothing, in
 * the order of the list. A batch is valid by construction: besides the rules of each {@link Write},
 *
 * <ul>
 *   <li>two writes do not address the same key, a {@link Check} included;
 *   <li>a {@link Put}, {@link Delete} or {@link Check} does not address a key at or under the
 *       prefix of a {@link DeletePrefix};
 *   <li>a namespace is created at most once.
 * </ul>
 *
 * @param writes the writes, in the order they are checked and applied
 */
public record Batch(List<Write> writes) {

  /**
   * @throws InvalidArgumentException if the batch breaks one of the rules
   */
  public Batch {
    Objects.requireNonNull(writes, "writes");
    writes = List.copyOf(writes);
    validate(writes);
  }

  public static Batch of(final Write... writes) {
    return new Batch(List.of(writes));
  }

  private static void validate(final List<Write> writes) {
    final var keys = new HashSet<StoreKey>();
    final var prefixes = new ArrayList<StoreKey>();
    final var namespaces = new HashSet<Namespace>();

    for (final var write : writes) {
      switch (write) {
        case final Put put -> requireUniqueKey(keys, put.key());
        case final Delete delete -> requireUniqueKey(keys, delete.key());
        case final Check check -> requireUniqueKey(keys, check.key());
        case final DeletePrefix deletePrefix -> {
          requireUniqueKey(keys, deletePrefix.prefix());
          prefixes.add(deletePrefix.prefix());
        }
        case final CreateNamespace create -> {
          if (!namespaces.add(create.namespace())) {
            throw new InvalidArgumentException(
                "Expected a batch to create namespace '%s' at most once, but it creates it again"
                    .formatted(create.namespace()));
          }
        }
      }
    }

    for (final var prefix : prefixes) {
      for (final var key : keys) {
        if (!key.equals(prefix) && key.isAtOrUnder(prefix)) {
          throw new InvalidArgumentException(
              "Expected no write in a batch at or under the deleted prefix '%s', but got key '%s'"
                  .formatted(prefix, key));
        }
      }
    }
  }

  private static void requireUniqueKey(final Set<StoreKey> keys, final StoreKey key) {
    if (!keys.add(key)) {
      throw new InvalidArgumentException(
          "Expected each key at most once in a batch, but got '%s' again".formatted(key));
    }
  }
}
