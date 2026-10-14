/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import java.util.Map;

/**
 * The state of the system partition, persisted as a single JSON file in each of its snapshots.
 *
 * @param version the format version of the persisted state, so later formats can tell older
 *     snapshots apart
 * @param entries the content of the {@link SystemKeyValueStore}, the latest value put under each
 *     {@link SystemPartitionKeyType#id() key id}
 */
public record SystemPartitionState(int version, Map<Long, byte[]> entries) {
  public static final int CURRENT_VERSION = 1;

  public SystemPartitionState {
    entries = entries == null ? Map.of() : Map.copyOf(entries);
  }

  public static SystemPartitionState initial() {
    return new SystemPartitionState(CURRENT_VERSION, Map.of());
  }
}
