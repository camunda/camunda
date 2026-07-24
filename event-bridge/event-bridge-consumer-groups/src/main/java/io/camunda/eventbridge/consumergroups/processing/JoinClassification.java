/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import java.util.Map;

/**
 * The resolved outcome of a successfully validated {@code JOIN_GROUP}: the registry-resolved
 * subscription every join carries, plus — for a static member whose {@code group.instance.id} is
 * already held by a live roster member — the id of the member it takes over from.
 *
 * <p>{@link #isTakeover()} is {@code false} for a dynamic member (no instance id), a fresh static
 * instance id, or an instance id whose previous holder already left or was evicted: {@code
 * ConsumerGroupState#findMemberByInstanceId} only ever returns a member currently in the replicated
 * roster, so a released instance id resolves to a normal (fresh-member) join.
 */
public record JoinClassification(Map<String, Integer> subscriptions, String takeoverOfMemberId) {

  public boolean isTakeover() {
    return takeoverOfMemberId != null;
  }
}
