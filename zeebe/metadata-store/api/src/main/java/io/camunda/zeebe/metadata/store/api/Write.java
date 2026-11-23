/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One write of a {@link ClusterMetadataStore#batch(java.util.List) batch}, as plain data. Each kind
 * carries only the arguments it needs. New kinds may be added; a consumer that switches over them
 * keeps a default branch.
 */
public sealed interface Write {

  /** Writes the value of one key, if the optional condition holds. Equality compares the bytes. */
  record Put(StoreKey key, byte[] value, @Nullable Condition condition) implements Write {

    public Put {
      Objects.requireNonNull(key, "key");
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

    public Delete {
      Objects.requireNonNull(key, "key");
    }

    /** An unconditional delete. */
    public Delete(final StoreKey key) {
      this(key, null);
    }
  }

  /** Deletes every key at or under the prefix, matched on whole segments. */
  record DeletePrefix(StoreKey prefix) implements Write {

    public DeletePrefix {
      Objects.requireNonNull(prefix, "prefix");
    }
  }

  /** Writes nothing: the batch only applies if the condition holds for this key. */
  record Check(StoreKey key, Condition condition) implements Write {

    public Check {
      Objects.requireNonNull(key, "key");
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
