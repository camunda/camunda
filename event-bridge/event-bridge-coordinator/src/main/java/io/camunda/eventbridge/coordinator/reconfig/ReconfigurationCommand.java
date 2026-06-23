/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.reconfig;

import io.camunda.eventbridge.coordinator.reconfig.ReconfigurationOp.Kind;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The wire form of a single reassignment step the change-coordinator sends to the broker that must
 * act ({@code op.member()}): join or leave one topic partition, with the partition's resulting
 * replica set and the topic's partition count (needed to set up routing topology on a fresh
 * joiner). The broker replies only once the Raft membership change has completed (the confirm).
 */
public record ReconfigurationCommand(
    Kind kind,
    String topic,
    int partitionId,
    int member,
    int partitionCount,
    List<Integer> members) {

  /** Cluster-communication subject for reassignment commands (request/reply). */
  public static final String SUBJECT = "event-bridge-reconfigure";

  /** {@code kind;topic;partitionId;member;partitionCount;m1,m2,...} */
  public byte[] encode() {
    final var ids = members.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    final var text =
        kind.name()
            + ";"
            + topic
            + ";"
            + partitionId
            + ";"
            + member
            + ";"
            + partitionCount
            + ";"
            + ids;
    return text.getBytes(StandardCharsets.UTF_8);
  }

  public static ReconfigurationCommand decode(final byte[] payload) {
    final var parts = new String(payload, StandardCharsets.UTF_8).split(";", 6);
    final List<Integer> members = new ArrayList<>();
    if (parts.length > 5 && !parts[5].isBlank()) {
      for (final var id : parts[5].split(",")) {
        members.add(Integer.parseInt(id));
      }
    }
    return new ReconfigurationCommand(
        Kind.valueOf(parts[0]),
        parts[1],
        Integer.parseInt(parts[2]),
        Integer.parseInt(parts[3]),
        Integer.parseInt(parts[4]),
        members);
  }
}
