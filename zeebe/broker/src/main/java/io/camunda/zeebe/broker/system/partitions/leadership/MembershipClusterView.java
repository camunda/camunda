/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.camunda.zeebe.mailbox.leadership.ClusterView;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The brokers of this broker's region that gossip sees, from the cluster membership. Gateways are
 * members too, but cannot lead, so only members that publish broker information count.
 */
public final class MembershipClusterView implements ClusterView {

  private final ClusterMembershipService membership;
  private final BrokerMemberId local;

  public MembershipClusterView(
      final ClusterMembershipService membership, final BrokerMemberId local) {
    this.membership = membership;
    this.local = local;
  }

  @Override
  public List<String> liveNodesInMyZone() {
    final var others =
        membership.getMembers().stream()
            .filter(Member::isReachable)
            .filter(member -> BrokerInfo.fromProperties(member.properties()) != null)
            .filter(member -> Objects.equals(member.id().zone(), local.zone()))
            .map(member -> member.id().id());
    return Stream.concat(Stream.of(local.id()), others).distinct().toList();
  }
}
