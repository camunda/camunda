/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.logging;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One structured log line per step of a partition's leadership handover, so that the phases of the
 * interruption can be put on one timeline across brokers and gateways: the transfer on the old
 * leader, the election and the leader transition on the new one, the switch of its stream processor
 * from replay to processing, its first command and response, and when each gateway routes to it.
 *
 * <p>Each line reads {@code handover event=<event> group=<partition group> partition=<number>
 * at=<epoch millis>} followed by the event's own {@code key=value} pairs. The lines are logged at
 * INFO by the {@value #LOGGER_NAME} logger and cost nothing when it is turned off.
 */
public final class HandoverTrace {

  public static final String LOGGER_NAME = "io.camunda.zeebe.handover";
  private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);
  private static final String PARTITION_INFIX = "-partition-";
  private static final String LOG_STREAM_PREFIX = "logStream-";

  private HandoverTrace() {}

  public static boolean isEnabled() {
    return LOG.isInfoEnabled();
  }

  /**
   * @param details alternating keys and values
   */
  public static void event(
      final String event,
      final String group,
      final int partition,
      final @Nullable Object... details) {
    if (!LOG.isInfoEnabled()) {
      return;
    }
    final var line =
        new StringBuilder(160)
            .append("handover event=")
            .append(event)
            .append(" group=")
            .append(group)
            .append(" partition=")
            .append(partition)
            .append(" at=")
            .append(System.currentTimeMillis());
    for (int i = 0; i + 1 < details.length; i += 2) {
      line.append(' ').append(details[i]).append('=').append(details[i + 1]);
    }
    LOG.info(line.toString());
  }

  /**
   * @param raftName the name of a Raft partition, {@code <group>-partition-<number>}, or of its log
   *     stream, {@code logStream-<group>-partition-<number>}
   */
  public static void raftEvent(
      final String event, final @Nullable String raftName, final @Nullable Object... details) {
    if (!LOG.isInfoEnabled()) {
      return;
    }
    if (raftName == null) {
      event(event, "unknown", 0, details);
      return;
    }
    final int infix = raftName.lastIndexOf(PARTITION_INFIX);
    if (infix < 0) {
      event(event, raftName, 0, details);
      return;
    }
    int partition;
    try {
      partition = Integer.parseInt(raftName.substring(infix + PARTITION_INFIX.length()));
    } catch (final NumberFormatException e) {
      partition = 0;
    }
    final var group = raftName.substring(0, infix);
    event(
        event,
        group.startsWith(LOG_STREAM_PREFIX) ? group.substring(LOG_STREAM_PREFIX.length()) : group,
        partition,
        details);
  }
}
