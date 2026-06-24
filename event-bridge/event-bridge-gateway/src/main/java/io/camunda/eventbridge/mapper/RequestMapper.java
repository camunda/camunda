/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.mapper;

import io.camunda.eventbridge.gateway.dto.EventBridgeDtos;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RequestMapper {

  public JoinGroupRequest toJoinGroupRequest(
      final String groupId, final EventBridgeDtos.JoinGroupRequest request) {
    return new JoinGroupRequest()
        .setGroupId(groupId)
        .setTopics(request.topics())
        .setInstanceId(request.instanceId());
  }

  public LeaveGroupRequest toLeaveGroupRequest(
      final String groupId,
      final String memberId,
      final EventBridgeDtos.LeaveGroupRequest request) {
    return new LeaveGroupRequest()
        .setGroupId(groupId)
        .setMemberId(memberId)
        .setMemberEpoch(request.memberEpoch());
  }

  public HeartbeatRequest toHeartbeatRequest(
      final String groupId, final String memberId, final EventBridgeDtos.HeartbeatRequest request) {
    final List<TopicPartition> owned = new ArrayList<>();
    if (request.ownedPartitions() != null) {
      request
          .ownedPartitions()
          .forEach(
              (topic, partitions) ->
                  partitions.forEach(p -> owned.add(new TopicPartition(topic, p))));
    }
    return new HeartbeatRequest()
        .setGroupId(groupId)
        .setMemberId(memberId)
        .setMemberEpoch(request.epoch())
        .setOwnedPartitions(owned);
  }

  public CommitOffsetRequest toCommitRequest(
      final String groupId, final String memberId, final EventBridgeDtos.CommitRequest request) {
    return new CommitOffsetRequest()
        .setGroupId(groupId)
        .setTopic(request.topic())
        .setMemberId(memberId)
        .setMemberEpoch(request.memberEpoch())
        .setPartitionId(request.partitionId())
        .setPosition(request.position());
  }
}
