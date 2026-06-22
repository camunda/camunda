/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.gateway.dto.EventBridgeDtos;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.HeartbeatRequest;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.LeaveGroupRequest;
import io.camunda.eventbridge.mapper.RequestMapper;
import io.camunda.eventbridge.mapper.ResponseMapper;
import io.camunda.eventbridge.service.CoordinatorService;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles consumer liveness signals: {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat}.
 *
 * <p>The first heartbeat from an unknown consumer auto-registers it with the coordinator — no prior
 * subscribe call is needed. The response carries the current epoch plus delta assignments ({@code
 * revoke}/{@code assign}) or, when an epoch advance has occurred, a full assignment list ({@code
 * fullAssignment}) that the consumer must reconcile against.
 *
 * <p>A {@code 503 Service Unavailable} response means the coordinator is temporarily unreachable.
 */
@RestController
@RequestMapping("/v1/groups")
public class CoordinationController {

  private final CoordinatorService coordinatorService;
  private final RequestMapper requestMapper;
  private final ResponseMapper responseMapper;

  public CoordinationController(
      final CoordinatorService coordinatorService,
      final RequestMapper requestMapper,
      final ResponseMapper responseMapper) {
    this.coordinatorService = coordinatorService;
    this.requestMapper = requestMapper;
    this.responseMapper = responseMapper;
  }

  @PostMapping("/{groupId}/join")
  public CompletableFuture<ResponseEntity<Object>> joinGroup(
      @PathVariable final String groupId,
      @RequestBody final EventBridgeDtos.JoinGroupRequest joinGroupRequest) {

    final var request = requestMapper.toJoinGroupRequest(groupId, joinGroupRequest);
    return coordinatorService
        .joinGroup(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.ok(null);
              }
              final var response = responseMapper.toJoinGroupResponse(res);
              return ResponseEntity.ok(response);
            });
  }

  @PostMapping("/{groupId}/consumers/{memberId}/heartbeat")
  public CompletableFuture<ResponseEntity<Object>> heartbeat(
      @PathVariable final String groupId,
      @PathVariable final String memberId,
      @RequestBody final HeartbeatRequest heartbeatRequest) {

    final var request = requestMapper.toHeartbeatRequest(groupId, memberId, heartbeatRequest);
    return coordinatorService
        .heartbeat(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.ok(null);
              }
              final var response = responseMapper.toHeartbeatResponse(res);
              return ResponseEntity.ok(response);
            });
  }

  @PostMapping("/{groupId}/consumers/{memberId}/leave")
  public CompletableFuture<ResponseEntity<Object>> leaveGroup(
      @PathVariable final String groupId,
      @PathVariable final String memberId,
      @RequestBody final LeaveGroupRequest leaveGroupRequest) {

    final var request = requestMapper.toLeaveGroupRequest(groupId, memberId, leaveGroupRequest);
    return coordinatorService
        .leaveGroup(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.ok(null);
              }
              final var response = responseMapper.toLeaveGroupResponse(res);
              return ResponseEntity.ok(response);
            });
  }

  @PostMapping("/{groupId}/consumers/{memberId}/commit")
  public CompletableFuture<ResponseEntity<Object>> commit(
      @PathVariable final String groupId,
      @PathVariable final String memberId,
      @RequestBody final EventBridgeDtos.CommitRequest commitRequest) {

    final var request = requestMapper.toCommitRequest(groupId, memberId, commitRequest);
    return coordinatorService
        .commit(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.internalServerError().build();
              }
              return ResponseEntity.ok(responseMapper.toCommitResponse(res));
            });
  }
}
