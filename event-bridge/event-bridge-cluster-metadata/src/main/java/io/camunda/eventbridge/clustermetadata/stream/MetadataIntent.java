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
 * Intents for the metadata stream. The engine dispatches commands to a processor by {@code
 * (ValueType, Intent)} and events to an applier by intent, so each command/event below maps to its
 * own processor/applier.
 */
public enum MetadataIntent implements Intent {
  /**
   * Command: internal upsert of a topic's desired configuration (status flips, reconfiguration).
   */
  REGISTER_TOPIC((short) 0, false),
  /** Event: the topic has been registered in replicated state. */
  TOPIC_REGISTERED((short) 1, true),
  /** Command: remove a topic from the registry (client request, validated in the processor). */
  DELETE_TOPIC((short) 2, false),
  /** Event: the topic has been removed from replicated state. */
  TOPIC_DELETED((short) 3, true),
  /** Command: create a topic (client request; the processor rejects an existing name). */
  CREATE_TOPIC((short) 4, false),
  /**
   * Command: reassign a topic's replicas (client request; the processor rejects an unknown name).
   */
  REASSIGN_TOPIC((short) 5, false);

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
      case 4 -> CREATE_TOPIC;
      case 5 -> REASSIGN_TOPIC;
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
