/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import java.util.Objects;

/**
 * One write of a {@link Batch}, as plain data. Each kind carries only the arguments it needs. A key
 * write rejects the empty key with {@link InvalidArgumentException}, so a whole namespace is never
 * written or cleared by one. New kinds may be added; a consumer that switches over them keeps a
 * default branch.
 */
public sealed interface Write {

  private static StoreKey requireKey(final StoreKey key, final String name) {
    Objects.requireNonNull(key, name);
    if (key.isEmpty()) {
      throw new InvalidArgumentException(
          "Expected a non-empty %s, but got the empty key of namespace '%s'"
              .formatted(name, key.namespace()));
    }
    return key;
  }

  /** Writes the value of one key, if the condition holds. */
  record Put(StoreKey key, Value value, Condition condition) implements Write {

    /**
     * @throws InvalidArgumentException if the key is empty
     */
    public Put {
      requireKey(key, "key");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(condition, "condition");
    }

    /** An unconditional put. */
    public Put(final StoreKey key, final Value value) {
      this(key, value, Condition.always());
    }
  }

  /** Deletes one key, if the condition holds. Keys underneath it are not touched. */
  record Delete(StoreKey key, Condition condition) implements Write {

    /**
     * @throws InvalidArgumentException if the key is empty
     */
    public Delete {
      requireKey(key, "key");
      Objects.requireNonNull(condition, "condition");
    }

    /** An unconditional delete. */
    public Delete(final StoreKey key) {
      this(key, Condition.always());
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
