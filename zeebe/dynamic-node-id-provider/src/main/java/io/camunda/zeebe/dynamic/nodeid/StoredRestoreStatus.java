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
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * A record that holds the restore status. The etag is used for conflict detection during updates.
 */
public record StoredRestoreStatus(RestoreStatus restoreStatus, String etag) {
  public record RestoreStatus(String restoreId, Set<Integer> restoredNodes) {

    public RestoreStatus(final String restoreId, final Set<Integer> restoredNodes) {
      this.restoreId = restoreId;
      this.restoredNodes =
          restoredNodes == null ? ImmutableSet.of() : ImmutableSet.copyOf(restoredNodes);
    }

    public boolean isNodeRestored(final int nodeId) {
      return restoredNodes != null && restoredNodes.contains(nodeId);
    }

    public RestoreStatus markNodeRestored(final int nodeId) {
      final var updatedCompletedNodes = new HashSet<>(restoredNodes());
      updatedCompletedNodes.add(nodeId);
      return new RestoreStatus(restoreId, updatedCompletedNodes);
    }

    public byte[] toJsonBytes(final ObjectMapper objectMapper) {
      try {
        return objectMapper.writeValueAsBytes(this);
      } catch (final JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    public static RestoreStatus fromJsonBytes(final ObjectMapper objectMapper, final byte[] json)
        throws IOException {
      return objectMapper.readValue(json, RestoreStatus.class);
    }
  }
}
