/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.dynamic.nodeid.repository.Metadata;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @param taskId the taskId that acquired this lease
 * @param timestamp the timestamp at which the lease expires
 * @param nodeInstance the nodeInstance this lease refers to
 * @param knownVersionMappings contains the versions of the other nodes that the node holding the
 *     lease is aware of.
 */
public record Lease(
    String taskId,
    long timestamp,
    NodeInstance nodeInstance,
    VersionMappings knownVersionMappings) {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public Lease {
    Objects.requireNonNull(taskId, "taskId cannot be null");
    if (taskId.isEmpty()) {
      throw new IllegalArgumentException("taskId cannot be empty");
    }
    Objects.requireNonNull(nodeInstance, "nodeInstance cannot be null");
    Objects.requireNonNull(knownVersionMappings, "knownVersionMappings cannot be null");
  }

  public static Lease fromMetadata(final Metadata metadata, final long expireAt, final int nodeId) {
    Objects.requireNonNull(metadata, "metadata cannot be null");
    if (metadata.task().isEmpty()) {
      throw new IllegalArgumentException("task cannot be empty");
    }
    final var nodeInstance = new NodeInstance(nodeId, metadata.version());
    return new Lease(
        metadata.task().get(), expireAt, nodeInstance, VersionMappings.of(nodeInstance));
  }

  public static Lease nextLease(
      final String taskId, final long expiry, final NodeInstance currentNodeInstance) {
    final var nodeInstance = currentNodeInstance.nextVersion();
    return new Lease(taskId, expiry, nodeInstance, VersionMappings.of(nodeInstance));
  }

  public String toJson(final ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsString(this);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public byte[] toJsonBytes(final ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsBytes(this);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static Lease fromJson(final ObjectMapper objectMapper, final String json) {
    try {
      return objectMapper.readValue(json, Lease.class);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static Lease fromJsonBytes(final ObjectMapper objectMapper, final byte[] json)
      throws IOException {
    return objectMapper.readValue(json, Lease.class);
  }

  public boolean isStillValid(final long now) {
    return now <= timestamp;
  }

  public Lease renew(
      final long now, final Duration leaseDuration, final VersionMappings knownVersionMappings) {
    if (!isStillValid(now)) {
      throw new IllegalStateException(
          "Lease is not valid anymore("
              + Instant.ofEpochMilli(now)
              + "), it expired at "
              + Instant.ofEpochMilli(timestamp));
    }
    final var millis = leaseDuration.toMillis();
    return new Lease(taskId, now + millis, nodeInstance, knownVersionMappings);
  }

  /**
   * Wrapper for mappings from NodeId -> Version. A sorted map is used so the order of the keys is
   * stable
   *
   * @param mappingsByNodeId
   */
  public record VersionMappings(SortedMap<Integer, Version> mappingsByNodeId) {

    private static final VersionMappings EMPTY = new VersionMappings(Collections.emptySortedMap());

    public VersionMappings(final Map<Integer, Version> mappingsByNodeId) {
      this(Collections.unmodifiableSortedMap(new TreeMap<>(mappingsByNodeId)));
    }

    public static VersionMappings of(final NodeInstance... nodeInstance) {
      final var map = new TreeMap<Integer, Version>();
      for (final var node : nodeInstance) {
        map.put(node.id(), node.version());
      }
      return new VersionMappings(Collections.unmodifiableSortedMap(map));
    }

    public static VersionMappings of(final Collection<NodeInstance> nodeInstances) {
      final var map = new TreeMap<Integer, Version>();
      for (final var node : nodeInstances) {
        map.put(node.id(), node.version());
      }
      return new VersionMappings(Collections.unmodifiableSortedMap(map));
    }

    public static VersionMappings empty() {
      return EMPTY;
    }
  }
}
