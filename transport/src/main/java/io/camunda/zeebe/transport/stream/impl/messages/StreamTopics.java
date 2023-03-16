/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

public enum StreamTopics {
  ADD("stream-add"),
  PUSH("stream-push"),
  REMOVE("stream-remove"),
  REMOVE_ALL("stream-remove-all");

  private final String topic;

  StreamTopics(final String topic) {
    this.topic = topic;
  }

  public String topic() {
    return topic;
  }
}
