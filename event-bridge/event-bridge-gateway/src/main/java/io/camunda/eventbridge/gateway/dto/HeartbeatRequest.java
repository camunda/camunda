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
 * Request body for {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat}.
 *
 * @param epoch the epoch value last seen by the consumer; {@code null} or {@code 0} for a new
 *     consumer that has never received an epoch
 * @param ownedPartitions the partitions the consumer currently holds, grouped {@code topic →
 *     [partition,...]}
 */
public record HeartbeatRequest(
    String memberId, Long epoch, Map<String, List<Integer>> ownedPartitions) {}
