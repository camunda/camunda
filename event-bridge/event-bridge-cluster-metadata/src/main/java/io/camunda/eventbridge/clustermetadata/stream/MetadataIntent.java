/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * Intents for the metadata stream. The {@link io.camunda.zeebe.stream.impl.StreamProcessor}
 * dispatches by {@code RecordType} (COMMAND → {@code process}, EVENT → {@code replay}), so these
 * intents are informational; the processor does not branch on them.
 */
public enum MetadataIntent implements Intent {
  /** Command: register (create or update) a topic's desired configuration. */
  REGISTER_TOPIC((short) 0, false),
  /** Event: the topic has been registered in replicated state. */
  TOPIC_REGISTERED((short) 1, true),
  /** Command: remove a topic from the registry. */
  DELETE_TOPIC((short) 2, false),
  /** Event: the topic has been removed from replicated state. */
  TOPIC_DELETED((short) 3, true);

  private final short value;
  private final boolean isEvent;

  MetadataIntent(final short value, final boolean isEvent) {
    this.value = value;
    this.isEvent = isEvent;
  }

  public static Intent from(final short value) {
    return switch (value) {
      case 0 -> REGISTER_TOPIC;
      case 1 -> TOPIC_REGISTERED;
      case 2 -> DELETE_TOPIC;
      case 3 -> TOPIC_DELETED;
      default -> Intent.UNKNOWN;
    };
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return isEvent;
  }
}
