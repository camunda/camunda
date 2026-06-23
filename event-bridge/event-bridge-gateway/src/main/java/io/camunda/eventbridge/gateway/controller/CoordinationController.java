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
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.service.CoordinatorService;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
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
                return coordinatorUnavailable();
              }
              return ResponseEntity.status(statusFor(res.getErrorCode()))
                  .body(responseMapper.toJoinGroupResponse(res));
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
                return coordinatorUnavailable();
              }
              // Surface the coordinator's error code as an HTTP status so the client can act on it
              // (e.g. rejoin on a fenced/unknown member) rather than silently treating every
              // heartbeat as a success.
              return ResponseEntity.status(statusFor(res.getErrorCode()))
                  .body(responseMapper.toHeartbeatResponse(res));
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
                return coordinatorUnavailable();
              }
              return ResponseEntity.status(statusFor(res.getErrorCode()))
                  .body(responseMapper.toLeaveGroupResponse(res));
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
                return coordinatorUnavailable();
              }
              // A fenced/unknown member must NOT read as a successful commit — map it to a status
              // (409) the client treats as "rejoin then retry", instead of a silent 200.
              return ResponseEntity.status(statusFor(res.getErrorCode()))
                  .body(responseMapper.toCommitResponse(res));
            });
  }

  /**
   * Maps a coordinator error code to the HTTP status the client reacts to.
   *
   * <ul>
   *   <li>{@code 200} — success, or a normal rebalance the client keeps polling through.
   *   <li>{@code 409} — the member is fenced/unknown (stale epoch, or the coordinator failed over
   *       and lost in-memory membership): the client must rejoin.
   *   <li>{@code 400} — malformed request (invalid group id).
   *   <li>{@code 500} — unexpected coordinator error.
   * </ul>
   */
  private static HttpStatus statusFor(final CoordinationErrorCode code) {
    return switch (code) {
      case NONE, REBALANCE_IN_PROGRESS -> HttpStatus.OK;
      case UNKNOWN_MEMBER_ID, FENCED_MEMBER_EPOCH, FENCED_MEMBER_ACTIVE, NOT_PARTITION_OWNER ->
          HttpStatus.CONFLICT;
      case INVALID_GROUP_ID -> HttpStatus.BAD_REQUEST;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  /** The coordinator partition leader was unreachable; the client should retry. */
  private static ResponseEntity<Object> coordinatorUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }
}
