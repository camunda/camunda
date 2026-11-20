/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import io.atomix.cluster.ClusterMembershipService;
import io.camunda.zeebe.engine.processing.policy.PolicyStateNotifier;
import io.camunda.zeebe.engine.processing.policy.PolicyStatus;
import org.jspecify.annotations.NullMarked;

/**
 * Gossips the policy state of a physical tenant as a property of the local member, as the topology
 * manager gossips the partition roles. SWIM notices the change on its next gossip round, tells the
 * local membership listeners, and spreads it to the other members. The value is not durable: the
 * engine publishes it again once partition 1 has recovered.
 */
@NullMarked
public final class GossipPolicyStateNotifier implements PolicyStateNotifier {

  private final ClusterMembershipService membershipService;
  private final String key;

  public GossipPolicyStateNotifier(
      final ClusterMembershipService membershipService, final String physicalTenantId) {
    this.membershipService = membershipService;
    key = PolicyStateGossip.key(physicalTenantId);
  }

  @Override
  public void publish(final PolicyStatus status) {
    membershipService
        .getLocalMember()
        .properties()
        .setProperty(key, PolicyStateGossip.encode(status));
  }

  @Override
  public void clear() {
    membershipService.getLocalMember().properties().remove(key);
  }
}
