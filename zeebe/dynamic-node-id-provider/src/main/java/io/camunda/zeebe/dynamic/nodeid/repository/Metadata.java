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
import java.util.Optional;

public record Metadata(Optional<String> task, Version version, boolean acquirable) {
  // KEYS MUST BE LOWERCASE
  private static final String TASK_ID_KEY = "taskid";
  private static final String VERSION_KEY = "version";
  private static final String ACQUIRABLE_KEY = "acquirable";

  public Metadata {
    Objects.requireNonNull(task, "task cannot be null");
    if (task.isPresent() && task.get().isEmpty()) {
      throw new IllegalArgumentException("task cannot be empty");
    }
    Objects.requireNonNull(version, "version cannot be null");
  }

  public Metadata(final Optional<String> task, final Version version) {
    this(task, version, true);
  }

  public Metadata forRelease() {
    return new Metadata(Optional.empty(), version, true);
  }

  public Metadata forUnusable() {
    return new Metadata(Optional.empty(), version, false);
  }

  public Metadata forAcquirable() {
    return new Metadata(Optional.empty(), version, true);
  }

  public static Metadata fromLease(final Lease lease) {
    return new Metadata(Optional.of(lease.taskId()), lease.nodeInstance().version(), true);
  }

  public Map<String, String> asMap() {
    return Map.of(
        TASK_ID_KEY, task.orElse(""),
        VERSION_KEY, Long.toString(version.version()),
        ACQUIRABLE_KEY, Boolean.toString(acquirable));
  }

  public static Metadata fromMap(final Map<String, String> map) {
    if (map.isEmpty()) {
      return null;
    }
    try {
      final var taskIdOpt = map.get(TASK_ID_KEY);
      final Optional<String> taskId =
          taskIdOpt != null && !taskIdOpt.isEmpty() ? Optional.of(taskIdOpt) : Optional.empty();
      final var version = new Version(Long.parseLong(map.get(VERSION_KEY)));
      final var acquirableStr = map.get(ACQUIRABLE_KEY);
      // Default to true for backwards compatibility with existing leases
      final var acquirable = acquirableStr == null || Boolean.parseBoolean(acquirableStr);
      return new Metadata(taskId, version, acquirable);
    } catch (final Exception e) {
      throw new IllegalArgumentException("Failed to deserialize metadata, map is " + map, e);
    }
  }
}
