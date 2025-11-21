/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.repository;

import io.camunda.zeebe.dynamic.nodeid.Lease;
import io.camunda.zeebe.dynamic.nodeid.Version;
import java.util.Map;
import java.util.Objects;

public record Metadata(String task, long expiry, Version version) {
  // KEYS MUST BE LOWERCASE
  private static final String TASK_ID_KEY = "taskid";
  private static final String EXPIRY_KEY = "expiry";
  private static final String VERSION_KEY = "version";

  public Metadata {
    Objects.requireNonNull(task, "task cannot be null");
    Objects.requireNonNull(task, "version cannot be null");
    if (expiry <= 0) {
      throw new IllegalArgumentException("expiry must be greater than zero");
    }
  }

  public static Metadata fromLease(final Lease lease) {
    return new Metadata(lease.taskId(), lease.timestamp(), lease.nodeInstance().version());
  }

  public Map<String, String> asMap() {
    return Map.of(
        TASK_ID_KEY,
        task,
        EXPIRY_KEY,
        String.valueOf(expiry),
        VERSION_KEY,
        Long.toString(version.version()));
  }

  public static Metadata fromMap(final Map<String, String> map) {
    if (map.isEmpty()) {
      return null;
    }
    try {
      final var taskId = map.get(TASK_ID_KEY);
      final var expiry = Long.parseLong(map.get(EXPIRY_KEY));
      final var version = new Version(Long.parseLong(map.get(VERSION_KEY)));
      return new Metadata(taskId, expiry, version);
    } catch (final Exception e) {
      throw new IllegalArgumentException("Failed to deserialize metadata, map is " + map, e);
    }
  }
}
