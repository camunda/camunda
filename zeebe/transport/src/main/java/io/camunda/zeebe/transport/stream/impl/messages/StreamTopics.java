/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import java.util.Objects;

public enum StreamTopics {
  ADD("stream-add"),
  PUSH("stream-push"),
  REMOVE("stream-remove"),
  REMOVE_ALL("stream-remove-all"),
  RESTART_STREAMS("stream-recreate");

  private final String topic;

  StreamTopics(final String topic) {
    this.topic = topic;
  }

  public String topic(final String physicalTenantId) {
    Objects.requireNonNull(physicalTenantId, "physicalTenantId must not be null");
    // remove this condition that leads to legacy topic in 8.11
    if (DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      return topic;
    }
    return physicalTenantId + "-" + topic;
  }

  /** Rolling-upgrade compat; remove alongside the legacy topic in 8.11. */
  public String dualTopic() {
    return DEFAULT_PHYSICAL_TENANT_ID + "-" + topic;
  }
}
