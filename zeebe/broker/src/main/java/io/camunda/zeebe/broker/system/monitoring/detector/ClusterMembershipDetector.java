/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.camunda.zeebe.broker.system.monitoring.history.HealthEvents;
import java.time.InstantSource;
import java.util.HashMap;

/**
 * Shows every other cluster member, as SWIM sees it from this broker, and marks a member degraded
 * while it is unreachable. Members joining and leaving are recorded as {@link #MEMBER_JOINED} and
 * {@link #MEMBER_LEFT} events. A member that restarts leaves and joins again, because SWIM replaces
 * it when it sees the new run.
 */
public final class ClusterMembershipDetector extends HealthDetector
    implements ClusterMembershipEventListener {

  public static final String MEMBER_JOINED = "MEMBER_JOINED";
  public static final String MEMBER_LEFT = "MEMBER_LEFT";
  private static final String NAME = "ClusterMembership";

  private final ClusterMembershipService membership;
  private final HealthEvents events;
  private final InstantSource clock;
  private final String localMember;

  public ClusterMembershipDetector(
      final ClusterMembershipService membership,
      final HealthEvents events,
      final InstantSource clock) {
    super(NAME);
    this.membership = membership;
    this.events = events;
    this.clock = clock;
    localMember = membership.getLocalMember().id().id();
    membership.getMembers().forEach(this::show);
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    final var member = event.subject();
    if (member.id().id().equals(localMember)) {
      return;
    }

    switch (event.type()) {
      case MEMBER_ADDED -> {
        showCurrent(member);
        events.record(
            NAME + "/" + child(member),
            MEMBER_JOINED,
            "member %s joined from %s".formatted(member.id().id(), member.address()),
            details(member));
      }
      case MEMBER_REMOVED -> {
        remove(child(member));
        events.record(
            NAME + "/" + child(member),
            MEMBER_LEFT,
            "member %s left or was replaced by a new run".formatted(member.id().id()),
            details(member));
      }
      case REACHABILITY_CHANGED -> showCurrent(member);
      default -> {}
    }
  }

  /**
   * Shows the member's current state. The event's subject is a copy whose {@link
   * Member#isReachable()} is always false, so the live member is read instead.
   */
  private void showCurrent(final Member subject) {
    final var current = membership.getMember(subject.id());
    if (current != null) {
      show(current);
    }
  }

  private void show(final Member member) {
    if (member.id().id().equals(localMember)) {
      return;
    }
    if (member.isReachable()) {
      markHealthy(child(member));
    } else {
      markDegraded(
          child(member),
          "member %s at %s is unreachable".formatted(member.id().id(), member.address()),
          clock.instant());
    }
  }

  private static String child(final Member member) {
    return "Member-" + member.id().id();
  }

  private static HashMap<String, String> details(final Member member) {
    final var details = new HashMap<String, String>();
    details.put("member", member.id().id());
    details.put("address", String.valueOf(member.address()));
    if (member.version() != null) {
      details.put("version", member.version().toString());
    }
    return details;
  }
}
