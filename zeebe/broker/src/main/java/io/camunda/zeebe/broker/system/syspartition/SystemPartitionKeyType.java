/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import java.util.Arrays;
import java.util.Optional;

/**
 * The keys of the system partition's key-value store. Each key is identified in the log and in
 * snapshots by its {@link #id()}, not its name or ordinal, so constants can be renamed or reordered
 * safely; an id must never be reused for a different key.
 */
public enum SystemPartitionKeyType {
  /** Placeholder until the store holds real cluster coordination data; used by tests. */
  TEST(1);

  private final long id;

  SystemPartitionKeyType(final long id) {
    this.id = id;
  }

  public long id() {
    return id;
  }

  /** Returns the key with the given id, or empty if this broker does not know it. */
  public static Optional<SystemPartitionKeyType> fromId(final long id) {
    return Arrays.stream(values()).filter(key -> key.id == id).findFirst();
  }
}
