/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import java.util.List;
import java.util.Map;

/**
 * Wire-DTO records exchanged with the group coordinator over JSON. Field names must match the
 * gateway DTOs exactly; these are serialized/deserialized by the client's HTTP transport.
 */
public final class CoordinationMessages {

  private CoordinationMessages() {}

  /** Body of {@code POST /v1/groups/{group}/join}. */
  public record JoinRequest(List<String> topics, String instanceId) {}

  /** Response to a join/rejoin request carrying the assigned member id and epoch. */
  public record JoinResponse(String errorCode, String memberId, Long memberEpoch) {}

  /** Body of {@code POST /v1/groups/{group}/consumers/{member}/leave}. */
  public record LeaveRequest(String memberId, long memberEpoch) {}

  /** Response to a leave request. */
  public record LeaveResponse(String errorCode) {}

  /** Body of {@code POST /v1/groups/{group}/consumers/{member}/commit}. */
  public record CommitRequest(String topic, int partitionId, long position, long memberEpoch) {}

  /** Body of {@code POST /v1/groups/{group}/consumers/{member}/heartbeat}. */
  public record HeartbeatRequest(long epoch, Map<String, List<Integer>> ownedPartitions) {}

  /**
   * Response to a heartbeat carrying either a delta ({@code revoke}/{@code assign}) or a full
   * reconciliation ({@code assignment}), plus any committed offsets to seed.
   */
  public record HeartbeatResponse(
      String errorCode,
      String memberId,
      long memberEpoch,
      Map<String, List<Integer>> revoke,
      Map<String, List<Integer>> assign,
      long assignmentEpoch,
      Map<String, List<Integer>> assignment,
      Map<String, Map<Integer, Long>> committedOffsets) {}
}
