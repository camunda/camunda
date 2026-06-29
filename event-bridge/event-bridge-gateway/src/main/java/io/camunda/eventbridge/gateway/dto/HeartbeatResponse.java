/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.dto;

import java.util.List;
import java.util.Map;

/**
 * Response body for {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat}.
 *
 * <p>Field names must match exactly what the client parses ({@code Consumer.sendHeartbeat}): {@code
 * memberEpoch} drives full-reconciliation detection and {@code assignment} carries the full target
 * set. They were previously named {@code epoch}/{@code fullAssignment}, which silently disabled the
 * client's full-reconciliation path.
 *
 * @param memberEpoch coordinator's current epoch; always {@code >= request.epoch}
 * @param revoke partitions the consumer must stop processing and acknowledge
 * @param assign partitions the consumer should start processing and acknowledge
 * @param assignment complete target assignment; applied when the coordinator epoch has advanced
 *     since the consumer's last heartbeat, in which case the consumer reconciles fully from it
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
