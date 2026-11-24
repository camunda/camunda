/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.state;

import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.StoreValue;
import java.util.List;
import java.util.Objects;

/**
 * One committed write as the state applies it: the operations that change the store, without
 * preconditions or previous values, which every replica derives when it applies the entry. The
 * serialized form is the log entry format's; this is what it decodes to.
 *
 * @param producerVersion the Camunda version of the writer, stored as the version of each namespace
 *     that the entry changes
 * @param timestamp the writer's clock when it wrote the entry, in milliseconds since the epoch;
 *     applying an entry reads no clock
 * @param namespaces the namespaces the entry changes, each at most once
 */
public record Entry(String producerVersion, long timestamp, List<NamespaceEntry> namespaces) {

  public Entry {
    Objects.requireNonNull(producerVersion, "producerVersion");
    namespaces = List.copyOf(namespaces);
  }

  /** The changes of one namespace: it is created, deleted, or only its keys change. */
  public sealed interface NamespaceEntry {

    Namespace namespace();

    /**
     * Creates the namespace, with the operations of the same batch on its keys.
     *
     * @param operations the operations on its keys, each key at most once
     */
    record Created(Namespace namespace, List<Operation> operations) implements NamespaceEntry {

      public Created {
        Objects.requireNonNull(namespace, "namespace");
        operations = List.copyOf(operations);
      }
    }

    /** Deletes the namespace with all its keys. */
    record Deleted(Namespace namespace) implements NamespaceEntry {

      public Deleted {
        Objects.requireNonNull(namespace, "namespace");
      }
    }

    /**
     * Changes keys of a namespace that exists.
     *
     * @param operations the operations on its keys, each key at most once
     */
    record KeysChanged(Namespace namespace, List<Operation> operations) implements NamespaceEntry {

      public KeysChanged {
        Objects.requireNonNull(namespace, "namespace");
        operations = List.copyOf(operations);
      }
    }
  }

  /** An operation on one key, addressed by its canonical key. */
  public sealed interface Operation {

    String key();

    /** Sets the current value of the key. */
    record Put(String key, StoreValue value) implements Operation {

      public Put {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
      }
    }

    /** Removes the key with both its values. */
    record Delete(String key) implements Operation {

      public Delete {
        Objects.requireNonNull(key, "key");
      }
    }
  }
}
