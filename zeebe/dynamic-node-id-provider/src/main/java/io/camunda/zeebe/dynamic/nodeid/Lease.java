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
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public record Lease(
    String taskId, long timestamp, NodeInstance nodeInstance, VersionMappings versionMappings) {

  public Lease {
    Objects.requireNonNull(taskId, "taskId cannot be null");
    if (taskId.isEmpty()) {
      throw new IllegalArgumentException("taskId cannot be empty");
    }
    Objects.requireNonNull(nodeInstance, "nodeInstance cannot be null");
    Objects.requireNonNull(versionMappings, "versionMappings cannot be null");
  }

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Wrapper for mappings from NodeId -> Version A sorted map is used to the order of the keys is
   * stable
   *
   * @param mappingsByNodeId
   */
  public record VersionMappings(SortedMap<Integer, Version> mappingsByNodeId) {

    public static VersionMappings of(NodeInstance... nodeInstance) {
      var map = new TreeMap<Integer, Version>();
      for (var node : nodeInstance) {
        map.put(node.id(), node.version());
      }
      return new VersionMappings(Collections.unmodifiableSortedMap(map));
    }

    public VersionMappings(Map<Integer, Version> mappingsByNodeId) {
      this(Collections.unmodifiableSortedMap(new TreeMap<>(mappingsByNodeId)));
    }

    private static final VersionMappings EMPTY = new VersionMappings(Collections.emptySortedMap());

    public static VersionMappings empty() {
      return EMPTY;
    }
  }

  public static Lease fromMetadata(Metadata metadata, int nodeId) {
    Objects.requireNonNull(metadata, "metadata cannot be null");
    var nodeInstance = new NodeInstance(nodeId, metadata.version());
    return new Lease(
        metadata.task(), metadata.expiry(), nodeInstance, VersionMappings.of(nodeInstance));
  }

  public static Lease from(String taskId, long expiry, NodeInstance currentNodeInstance) {
    var nodeInstance = currentNodeInstance.nextVersion();
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

  public Lease renew(final long now, final Duration leaseDuration) {
    if (!isStillValid(now)) {
      throw new IllegalStateException(
          "Lease is not valid anymore("
              + Instant.ofEpochMilli(now)
              + "), it expired at "
              + Instant.ofEpochMilli(timestamp));
    }
    final var millis = leaseDuration.toMillis();
    return new Lease(taskId, now + millis, nodeInstance, versionMappings);
  }
}
