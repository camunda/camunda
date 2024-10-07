/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.impl;

import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.discovery.NodeDiscoveryEvent;
import io.atomix.cluster.discovery.NodeDiscoveryEventListener;
import io.atomix.cluster.discovery.NodeDiscoveryService;
import io.atomix.cluster.protocol.GroupMembershipEvent;
import io.atomix.cluster.protocol.GroupMembershipEventListener;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.cluster.protocol.GroupMembershipProtocolConfig;
import io.atomix.utils.event.AbstractListenerManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link GroupMembershipProtocol} implementation which uses the node discovery service to add and
 * remove new members. Members can be either initially provided as is, or will be added/removed as
 * nodes are added/removed in the associated {@link NodeDiscoveryService} passed during {@link
 * #join(BootstrapService, NodeDiscoveryService, Member)}.
 */
public final class DiscoveryMembershipProtocol
    extends AbstractListenerManager<GroupMembershipEvent, GroupMembershipEventListener>
    implements GroupMembershipProtocol, NodeDiscoveryEventListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryMembershipProtocol.class);

  private final ConcurrentMap<MemberId, Member> members = new ConcurrentHashMap<>();
  private final AtomicBoolean started = new AtomicBoolean();

  private NodeDiscoveryService discoveryService;

  public DiscoveryMembershipProtocol() {}

  public DiscoveryMembershipProtocol(final Config config) {
    this(config.members);
  }

  public DiscoveryMembershipProtocol(final Map<MemberId, Member> members) {
    this.members.putAll(members);
  }

  @Override
  public Set<Member> getMembers() {
    return new HashSet<>(members.values());
  }

  @Override
  public Member getMember(final MemberId memberId) {
    return members.get(memberId);
  }

  @Override
  public CompletableFuture<Void> join(
      final BootstrapService bootstrap,
      final NodeDiscoveryService discovery,
      final Member localMember) {
    if (started.compareAndSet(false, true)) {
      discovery
          .getNodes()
          .forEach(
              n -> {
                final var memberId = MemberId.from(n.id().id());
                members.put(memberId, Member.member(memberId, n.address()));
              });
      members.put(localMember.id(), localMember);
      post(new GroupMembershipEvent(GroupMembershipEvent.Type.MEMBER_ADDED, localMember));

      discoveryService = discovery;
      discoveryService.addListener(this);
      LOGGER.info("Started discovery membership protocol with members [{}]", members);
    }

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> join(
      final BootstrapService bootstrap,
      final NodeDiscoveryService discovery,
      final Member localMember,
      final String actorSchedulerName) {
    return join(bootstrap, discovery, localMember);
  }

  @Override
  public CompletableFuture<Void> leave(final Member localMember) {
    if (started.compareAndSet(true, false)) {
      LOGGER.info("Stopped discovery membership protocol");
      discoveryService.removeListener(this);
      members.clear();
    }

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void event(final NodeDiscoveryEvent event) {
    final var node = event.node();
    final var memberId = MemberId.from(node.id().id());
    final var member = Member.member(memberId, node.address());
    if (event.type() == NodeDiscoveryEvent.Type.JOIN) {
      if (members.put(memberId, member) == null) {
        post(new GroupMembershipEvent(GroupMembershipEvent.Type.MEMBER_ADDED, member));
      }
    } else if (event.type() == NodeDiscoveryEvent.Type.LEAVE) {
      if (members.remove(memberId) != null) {
        post(new GroupMembershipEvent(GroupMembershipEvent.Type.MEMBER_REMOVED, member));
      }
    }
  }

  @Override
  public GroupMembershipProtocolConfig config() {
    return new Config(new HashMap<>(members));
  }

  public static final class Config extends GroupMembershipProtocolConfig {
    private final Map<MemberId, Member> members;

    public Config(final Map<MemberId, Member> members) {
      this.members = members;
    }

    public Map<MemberId, Member> members() {
      return members;
    }

    @Override
    public GroupMembershipProtocol.Type<Config> getType() {
      return new Type();
    }
  }

  private static final class Type implements GroupMembershipProtocol.Type<Config> {
    @Override
    public String name() {
      return "memory";
    }

    @Override
    public GroupMembershipProtocol newProtocol(final Config config) {
      return new DiscoveryMembershipProtocol(config);
    }
  }
}
