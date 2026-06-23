/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * Intents for the coordinator stream. The {@link io.camunda.zeebe.stream.impl.StreamProcessor}
 * dispatches by {@code RecordType} (COMMAND → {@code process}, EVENT → {@code replay}), so these
 * intents are informational; the processor does not branch on them.
 */
public enum CoordinatorIntent implements Intent {
  /** Command: a consumer asks the coordinator to commit an offset. */
  COMMIT_OFFSET((short) 0, false),
  /** Event: the offset has been (monotonically) committed to replicated state. */
  OFFSET_COMMITTED((short) 1, true),
  /** Command: persist a group's membership/assignment after a rebalance. */
  REBALANCE_GROUP((short) 2, false),
  /** Event: the group metadata has been committed to replicated state. */
  GROUP_METADATA_COMMITTED((short) 3, true),
  /** Command: register (create or update) a topic's desired configuration. */
  REGISTER_TOPIC((short) 4, false),
  /** Event: the topic has been registered in replicated state. */
  TOPIC_REGISTERED((short) 5, true),
  /** Command: remove a topic from the registry. */
  DELETE_TOPIC((short) 6, false),
  /** Event: the topic has been removed from replicated state. */
  TOPIC_DELETED((short) 7, true);

  private final short value;
  private final boolean isEvent;

  CoordinatorIntent(final short value, final boolean isEvent) {
    this.value = value;
    this.isEvent = isEvent;
  }

  public static Intent from(final short value) {
    return switch (value) {
      case 0 -> COMMIT_OFFSET;
      case 1 -> OFFSET_COMMITTED;
      case 2 -> REBALANCE_GROUP;
      case 3 -> GROUP_METADATA_COMMITTED;
      case 4 -> REGISTER_TOPIC;
      case 5 -> TOPIC_REGISTERED;
      case 6 -> DELETE_TOPIC;
      case 7 -> TOPIC_DELETED;
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
