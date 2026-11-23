/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One write of a {@link ClusterMetadataStore#batch(java.util.List) batch}, as plain data. Each kind
 * carries only the arguments it needs. A key write rejects the empty key with {@link
 * InvalidArgumentException}, so a whole namespace is never written or cleared by one. New kinds may
 * be added; a consumer that switches over them keeps a default branch.
 */
public sealed interface Write {

  /**
   * Checks the rules of a batch that need no store state, in addition to the rules of each record:
   *
   * <ul>
   *   <li>two records do not address the same key, a {@link Check} included;
   *   <li>a {@link Put}, {@link Delete} or {@link Check} does not address a key at or under the
   *       prefix of a {@link DeletePrefix};
   *   <li>a namespace is created at most once.
   * </ul>
   *
   * @throws InvalidArgumentException if the batch breaks one of the rules
   */
  static void validateBatch(final List<? extends Write> writes) {
    Objects.requireNonNull(writes, "writes");
    final var keys = new HashSet<StoreKey>();
    final var prefixes = new ArrayList<StoreKey>();
    final var namespaces = new HashSet<Namespace>();

    for (final var write : writes) {
      Objects.requireNonNull(write, "write");
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

  private static void requireUniqueKey(final HashSet<StoreKey> keys, final StoreKey key) {
    if (!keys.add(key)) {
      throw new InvalidArgumentException(
          "Expected each key at most once in a batch, but got '%s' again".formatted(key));
    }
  }

  private static StoreKey requireKey(final StoreKey key, final String name) {
    Objects.requireNonNull(key, name);
    if (key.isEmpty()) {
      throw new InvalidArgumentException(
          "Expected a non-empty %s, but got the empty key of namespace '%s'"
              .formatted(name, key.namespace()));
    }
    return key;
  }

  /** Writes the value of one key, if the optional condition holds. Equality compares the bytes. */
  record Put(StoreKey key, byte[] value, @Nullable Condition condition) implements Write {

    /**
     * @throws InvalidArgumentException if the key is empty
     */
    public Put {
      requireKey(key, "key");
      Objects.requireNonNull(value, "value");
    }

    /** An unconditional put. */
    public Put(final StoreKey key, final byte[] value) {
      this(key, value, null);
    }

    @Override
    public boolean equals(final Object o) {
      return o
              instanceof
              Put(final StoreKey otherKey, final byte[] otherValue, final Condition otherCondition)
          && key.equals(otherKey)
          && Arrays.equals(value, otherValue)
          && Objects.equals(condition, otherCondition);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, Arrays.hashCode(value), condition);
    }

    @Override
    public String toString() {
      return "Put[key=%s, value=%s, condition=%s]"
          .formatted(key, Arrays.toString(value), condition);
    }
  }

  /** Deletes one key, if the optional condition holds. Keys underneath it are not touched. */
  record Delete(StoreKey key, @Nullable Condition condition) implements Write {

    /**
     * @throws InvalidArgumentException if the key is empty
     */
    public Delete {
      requireKey(key, "key");
    }

    /** An unconditional delete. */
    public Delete(final StoreKey key) {
      this(key, null);
    }
  }

  /** Deletes every key at or under the prefix, matched on whole segments. */
  record DeletePrefix(StoreKey prefix) implements Write {

    /**
     * @throws InvalidArgumentException if the prefix is empty
     */
    public DeletePrefix {
      requireKey(prefix, "prefix");
    }
  }

  /** Writes nothing: the batch only applies if the condition holds for this key. */
  record Check(StoreKey key, Condition condition) implements Write {

    /**
     * @throws InvalidArgumentException if the key is empty
     */
    public Check {
      requireKey(key, "key");
      Objects.requireNonNull(condition, "condition");
    }
  }

  /** Creates the namespace, so that the writes after it in the batch can address it. */
  record CreateNamespace(Namespace namespace) implements Write {

    public CreateNamespace {
      Objects.requireNonNull(namespace, "namespace");
    }
  }
}
