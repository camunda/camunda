/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator.stream;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact line-based encoding for replicated consumer-group metadata. The payload is what flows
 * through the coordinator stream (replicated + snapshotted) and is replayed to rebuild the
 * in-memory registry after a coordinator failover, so consumers re-attach with their existing epoch
 * instead of a full rejoin.
 *
 * <pre>
 *   line 0: &lt;assignmentEpoch&gt;
 *   line n: &lt;memberId&gt;|&lt;instanceId&gt;|&lt;memberEpoch&gt;|&lt;p1,p2,...&gt;
 * </pre>
 */
public final class GroupMetadataCodec {

  private GroupMetadataCodec() {}

  /** A replicated snapshot of a consumer group's membership and assignment. */
  public record GroupMetadata(int assignmentEpoch, List<MemberSnapshot> members) {}

  /** One member's replicated state: identity, epoch (for fencing), and assigned partitions. */
  public record MemberSnapshot(
      String memberId, String instanceId, long memberEpoch, List<Integer> partitions) {}

  public static String encode(final GroupMetadata metadata) {
    final var sb = new StringBuilder();
    sb.append(metadata.assignmentEpoch());
    for (final var member : metadata.members()) {
      sb.append('\n')
          .append(member.memberId())
          .append('|')
          .append(member.instanceId() == null ? "" : member.instanceId())
          .append('|')
          .append(member.memberEpoch())
          .append('|')
          .append(joinInts(member.partitions()));
    }
    return sb.toString();
  }

  public static GroupMetadata decode(final String payload) {
    final var lines = payload.split("\n", -1);
    final int assignmentEpoch = Integer.parseInt(lines[0]);
    final List<MemberSnapshot> members = new ArrayList<>();
    for (int i = 1; i < lines.length; i++) {
      if (lines[i].isEmpty()) {
        continue;
      }
      final var parts = lines[i].split("\\|", -1);
      final var instanceId = parts[1].isEmpty() ? null : parts[1];
      members.add(
          new MemberSnapshot(parts[0], instanceId, Long.parseLong(parts[2]), splitInts(parts[3])));
    }
    return new GroupMetadata(assignmentEpoch, members);
  }

  private static String joinInts(final List<Integer> values) {
    final var sb = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(values.get(i));
    }
    return sb.toString();
  }

  private static List<Integer> splitInts(final String csv) {
    final List<Integer> result = new ArrayList<>();
    if (csv.isEmpty()) {
      return result;
    }
    for (final var token : csv.split(",")) {
      result.add(Integer.parseInt(token));
    }
    return result;
  }
}
