/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.mapper;

import io.camunda.eventbridge.gateway.dto.CommitResponse;
import io.camunda.eventbridge.gateway.dto.HeartbeatResponse;
import io.camunda.eventbridge.gateway.dto.JoinGroupResponse;
import io.camunda.eventbridge.gateway.dto.LeaveGroupResponse;
import io.camunda.eventbridge.gateway.dto.OffsetFetchResponse;
import io.camunda.eventbridge.gateway.dto.PublishBatchResponse;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetResponse;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Maps broker-client coordination responses ({@code io.camunda.eventbridge.protocol.request.*}) to
 * the gateway's REST response DTOs. The two layers share several simple names (e.g. {@code
 * JoinGroupResponse}), so the DTO type is imported and the protocol type is referenced by its
 * fully-qualified name on the method parameter — Java allows only one import per simple name.
 */
@Component
public class ResponseMapper {

  public JoinGroupResponse toJoinGroupResponse(
      final io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse response) {
    return new JoinGroupResponse(
        response.getErrorCode().getId(), response.getMemberId(), response.getMemberEpoch());
  }

  public LeaveGroupResponse toLeaveGroupResponse(
      final io.camunda.eventbridge.protocol.request.coordination.LeaveGroupResponse response) {
    return new LeaveGroupResponse(response.getErrorCode().getId());
  }

  public CommitResponse toCommitResponse(final CommitOffsetResponse response) {
    return new CommitResponse(response.getErrorCode().getId(), response.getCommittedPosition());
  }

  public HeartbeatResponse toHeartbeatResponse(
      final io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse response) {
    return new HeartbeatResponse(
        response.getErrorCode().getId(),
        response.getMemberId(),
        response.getMemberEpoch(),
        groupByTopic(response.getRevoke()),
        groupByTopic(response.getAssign()),
        response.getAssignmentEpoch(),
        groupByTopic(response.getAssignment()),
        groupOffsetsByTopic(response.getCommittedOffsets()));
  }

  public OffsetFetchResponse toOffsetFetchResponse(
      final io.camunda.eventbridge.protocol.request.coordination.OffsetFetchResponse response) {
    return new OffsetFetchResponse(
        response.getErrorCode().getId(), groupOffsetsByTopic(response.getCommittedOffsets()));
  }

  /** Groups a flat partition list into {@code topic → [partition,...]} (partitions sorted). */
  private static Map<String, List<Integer>> groupByTopic(final List<TopicPartition> partitions) {
    final Map<String, List<Integer>> byTopic = new LinkedHashMap<>();
    partitions.forEach(
        p -> byTopic.computeIfAbsent(p.topic(), ignored -> new ArrayList<>()).add(p.partition()));
    byTopic.values().forEach(Collections::sort);
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

  public PublishBatchResponse toPublishBatchResponse(
      final io.camunda.eventbridge.protocol.request.PublishBatchResponse response) {
    return new PublishBatchResponse(
        List.of(response.getFirstPosition(), response.getLastPosition()));
  }
}
