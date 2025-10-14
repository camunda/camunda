/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper.lease;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.clustering.mapper.NodeInstance;
import java.io.IOException;
import java.time.Duration;

public interface LeaseClient {

  String taskId();

  void initialize();

  Lease acquireLease();

  void setNodeIdMappings(NodeIdMappings nodeIdMappings);

  Lease renewLease();

  Lease currentLease();

  void releaseLease();

  record Lease(
      String taskId, long timestamp, NodeInstance nodeInstance, NodeIdMappings nodeIdMappings) {
    public Lease(final String taskId, final long timestamp, final NodeInstance nodeInstance) {
      this(taskId, timestamp, nodeInstance, NodeIdMappings.empty());
    }

    public String toJson(final ObjectMapper objectMapper)
        throws com.fasterxml.jackson.core.JsonProcessingException {
      return objectMapper.writeValueAsString(this);
    }

    public byte[] toJsonBytes(final ObjectMapper objectMapper)
        throws com.fasterxml.jackson.core.JsonProcessingException {
      return objectMapper.writeValueAsBytes(this);
    }

    public static Lease fromJson(final ObjectMapper objectMapper, final String json)
        throws com.fasterxml.jackson.core.JsonProcessingException {
      return objectMapper.readValue(json, Lease.class);
    }

    public static Lease fromJsonBytes(final ObjectMapper objectMapper, final byte[] json)
        throws IOException {
      return objectMapper.readValue(json, Lease.class);
    }

    public boolean isStillValid(final long now, final Duration expireDuration) {
      return timestamp + expireDuration.toMillis() > now;
    }

    public Lease renew(final long now, final long millis) {
      return new Lease(taskId, now + millis, nodeInstance, nodeIdMappings);
    }
  }
}
