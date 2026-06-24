/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.mapper;

import io.camunda.eventbridge.gateway.dto.EventBridgeDtos;
import io.camunda.eventbridge.protocol.request.PublishBatchResponse;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetResponse;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupResponse;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchResponse;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class ResponseMapper {

  public EventBridgeDtos.JoinGroupResponse toJoinGroupResponse(final JoinGroupResponse response) {
    return new EventBridgeDtos.JoinGroupResponse(
        response.getErrorCode().getId(), response.getMemberId(), response.getMemberEpoch());
  }

  public EventBridgeDtos.LeaveGroupResponse toLeaveGroupResponse(
      final LeaveGroupResponse response) {
    return new EventBridgeDtos.LeaveGroupResponse(response.getErrorCode().getId());
  }

  public EventBridgeDtos.CommitResponse toCommitResponse(final CommitOffsetResponse response) {
    return new EventBridgeDtos.CommitResponse(
        response.getErrorCode().getId(), response.getCommittedPosition());
  }

  public EventBridgeDtos.HeartbeatResponse toHeartbeatResponse(final HeartbeatResponse response) {
    return new EventBridgeDtos.HeartbeatResponse(
        response.getErrorCode().getId(),
        response.getMemberId(),
        response.getMemberEpoch(),
        groupByTopic(response.getRevoke()),
        groupByTopic(response.getAssign()),
        response.getAssignmentEpoch(),
        groupByTopic(response.getAssignment()),
        groupOffsetsByTopic(response.getCommittedOffsets()));
  }

  public EventBridgeDtos.OffsetFetchResponse toOffsetFetchResponse(
      final OffsetFetchResponse response) {
    return new EventBridgeDtos.OffsetFetchResponse(
        response.getErrorCode().getId(), groupOffsetsByTopic(response.getCommittedOffsets()));
  }

  /** Groups a flat partition list into {@code topic → [partition,...]} (partitions sorted). */
  private static Map<String, List<Integer>> groupByTopic(final List<TopicPartition> partitions) {
    final Map<String, List<Integer>> byTopic = new LinkedHashMap<>();
    partitions.forEach(
        p -> byTopic.computeIfAbsent(p.topic(), ignored -> new ArrayList<>()).add(p.partition()));
    byTopic.values().forEach(java.util.Collections::sort);
    return byTopic;
  }

  /** Groups committed offsets into {@code topic → (partition → offset)} (partitions sorted). */
  private static Map<String, Map<Integer, Long>> groupOffsetsByTopic(
      final Map<TopicPartition, Long> offsets) {
    final Map<String, Map<Integer, Long>> byTopic = new LinkedHashMap<>();
    offsets.forEach(
        (tp, offset) ->
            byTopic
                .computeIfAbsent(tp.topic(), ignored -> new TreeMap<>())
                .put(tp.partition(), offset));
    return byTopic;
  }

  public EventBridgeDtos.PublishBatchResponse toPublishBatchResponse(
      final PublishBatchResponse response) {
    return new EventBridgeDtos.PublishBatchResponse(
        List.of(response.getFirstPosition(), response.getLastPosition()));
  }
}
