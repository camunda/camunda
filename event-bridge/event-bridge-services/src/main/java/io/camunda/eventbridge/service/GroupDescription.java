/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.service;

import java.util.Map;

/**
 * One consumer group's observable description (the domain result of {@link
 * CoordinatorService#describeGroups}) — its lifecycle state, epochs, subscription ({@code topic →
 * partitionCount}), and members ({@code memberId → assignedEpoch}; a member whose assignedEpoch is
 * below the groupEpoch is lagging the current rebalance). The gateway serialises it directly for
 * {@code GET /v1/groups}.
 */
public record GroupDescription(
    String groupId,
    String state,
    long groupEpoch,
    long assignmentEpoch,
    Map<String, Integer> subscriptions,
    Map<String, Long> members) {}
