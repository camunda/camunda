/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

public enum StreamTopics {
  ADD("stream-add"),
  PUSH("stream-push"),
  REMOVE("stream-remove"),
  REMOVE_ALL("stream-remove-all"),
  RESTART_STREAMS("stream-recreate");

  public static final String DEFAULT_GROUP = "default";

  private final String topic;

  StreamTopics(final String topic) {
    this.topic = topic;
  }

  public String topic() {
    return topic;
  }

  public String topic(final String physicalTenantId) {
    if (DEFAULT_GROUP.equals(physicalTenantId)) {
      return topic;
    }
    return physicalTenantId + "-" + topic;
  }
}
