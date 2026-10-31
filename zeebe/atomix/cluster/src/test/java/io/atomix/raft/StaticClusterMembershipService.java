/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.utils.net.Address;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * Implementation of the {@link ClusterMembershipService} that just manages a fixed set of members,
 * to be used in tests.
 */
final class StaticClusterMembershipService implements ClusterMembershipService {
  private final Member localMember;
  private final HashMap<MemberId, Member> members;

  private StaticClusterMembershipService(
      final Member localMember, final HashMap<MemberId, Member> members) {
    this.localMember = localMember;
    this.members = members;
  }

  static ClusterMembershipService of(final MemberId localId, final MemberId... remoteIds) {
    final var localMember = Member.member(localId, Address.local());
    final var remoteMembers =
        Arrays.stream(remoteIds).map(id -> Member.member(id, Address.local()));
    final var members = new HashMap<MemberId, Member>();
    members.put(localId, localMember);
    remoteMembers.forEach(member -> members.put(member.id(), member));
    return new StaticClusterMembershipService(localMember, members);
  }

  @Override
  public Member getLocalMember() {
    return localMember;
  }

  @Override
  public Set<Member> getMembers() {
    return Set.copyOf(members.values());
  }

  @Override
  public Member getMember(final MemberId memberId) {
    return members.get(memberId);
  }

  @Override
  public void addListener(final ClusterMembershipEventListener listener) {}

  @Override
  public void removeListener(final ClusterMembershipEventListener listener) {}
}
