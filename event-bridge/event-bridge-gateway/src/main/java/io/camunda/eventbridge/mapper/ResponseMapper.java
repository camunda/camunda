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
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupResponse;
import java.util.List;
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

  public EventBridgeDtos.HeartbeatResponse toHeartbeatResponse(final HeartbeatResponse response) {
    return new EventBridgeDtos.HeartbeatResponse(
        response.getErrorCode().getId(),
        response.getMemberId(),
        response.getMemberEpoch(),
        response.getRevoke(),
        response.getAssign(),
        response.getAssignmentEpoch(),
        response.getAssignment());
  }

  public EventBridgeDtos.PublishBatchResponse toPublishBatchResponse(
      final PublishBatchResponse response) {
    return new EventBridgeDtos.PublishBatchResponse(
        List.of(response.getFirstPosition(), response.getLastPosition()));
  }
}
