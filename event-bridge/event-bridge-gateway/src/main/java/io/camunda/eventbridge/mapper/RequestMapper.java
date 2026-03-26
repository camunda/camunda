/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.mapper;

import io.camunda.eventbridge.gateway.dto.EventBridgeDtos;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import org.springframework.stereotype.Component;

@Component
public class RequestMapper {

  public JoinGroupRequest toJoinGroupRequest(
      final String groupId, final EventBridgeDtos.JoinGroupRequest request) {
    return new JoinGroupRequest().setGroupId(groupId).setInstanceId(request.instanceId());
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
    return new HeartbeatRequest()
        .setGroupId(groupId)
        .setMemberId(memberId)
        .setMemberEpoch(request.epoch())
        .setOwnedPartitions(request.ownedPartitions());
  }
}
