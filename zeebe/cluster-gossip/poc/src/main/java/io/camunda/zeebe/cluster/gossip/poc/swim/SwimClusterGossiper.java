/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.swim;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.poc.core.AbstractClusterGossiper;
import io.camunda.zeebe.cluster.gossip.poc.core.GlobalEntry;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Proof of concept: a gossiper that carries all state in SWIM member properties.
 *
 * <ul>
 *   <li>A MEMBER value is a property of its owner, {@code gossip.m.<name>} = base64 of the value.
 *       Its lifetime is the owner's SWIM membership: SWIM drops a member's properties when the
 *       member is removed or restarts (a new instance id replaces the member).
 *   <li>Every member publishes its whole merged view of GLOBAL values in its own properties, {@code
 *       gossip.g.<name>}. Readers merge the copies of all members. Because every member republishes
 *       what it merged, a value survives as long as any member holds it, not just its writer.
 * </ul>
 *
 * <p>SWIM polls the local properties every gossip interval, and on a change gossips the member's
 * <em>whole</em> property set over UDP to a few peers, which relay it. A full TCP sync every sync
 * interval re-delivers everything, so values dropped because a key was not registered yet arrive
 * again; on registration this gossiper also re-reads the current properties of all members, which
 * SWIM keeps as state.
 *
 * <p>Assumptions of the proof of concept: values are base64 strings in {@link Properties}, so they
 * grow by a third; the gossiper owns every property starting with {@code gossip.}.
 */
public final class SwimClusterGossiper extends AbstractClusterGossiper
    implements ClusterMembershipEventListener {

  static final String MEMBER_PREFIX = "gossip.m.";
  static final String GLOBAL_PREFIX = "gossip.g.";
  private static final Base64.Encoder ENCODER = Base64.getEncoder();
  private static final Base64.Decoder DECODER = Base64.getDecoder();

  private final ClusterMembershipService membershipService;

  public SwimClusterGossiper(final ClusterMembershipService membershipService) {
    this(membershipService, System::currentTimeMillis);
  }

  public SwimClusterGossiper(
      final ClusterMembershipService membershipService, final LongSupplier clock) {
    super(membershipService.getLocalMember().id(), clock);
    this.membershipService = membershipService;
  }

  /** Starts listening to membership changes and reads the current state of all members. */
  @Override
  public void start() {
    membershipService.addListener(this);
    withLock(
        notifications -> {
          for (final Member member : membershipService.getMembers()) {
            if (!member.id().equals(localMemberId)) {
              ingest(member.id(), member.properties(), null, notifications);
            }
          }
        });
  }

  @Override
  public void close() {
    membershipService.removeListener(this);
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    final var member = event.subject();
    if (member.id().equals(localMemberId)) {
      return;
    }
    switch (event.type()) {
      case MEMBER_ADDED, METADATA_CHANGED ->
          withLock(notifications -> ingest(member.id(), member.properties(), null, notifications));
      case MEMBER_REMOVED ->
          withLock(notifications -> removeMemberValues(member.id(), notifications));
      default -> {}
    }
  }

  @Override
  protected void onGlobalChanged(final String name, final GlobalEntry entry) {
    publish(GLOBAL_PREFIX + name, encodeGlobal(entry));
  }

  @Override
  protected void onLocalMemberValueChanged(final String name, final byte[] bytes) {
    publish(MEMBER_PREFIX + name, ENCODER.encodeToString(bytes));
  }

  @Override
  protected void onKeyRegistered(final GossipKey<?> key, final List<Runnable> notifications) {
    // custom-merge values of unregistered keys were dropped; SWIM still holds them as member state
    if (key instanceof GlobalKey<?>) {
      for (final Member member : membershipService.getMembers()) {
        if (!member.id().equals(localMemberId)) {
          ingest(member.id(), member.properties(), key.name(), notifications);
        }
      }
    }
  }

  private void publish(final String property, final String value) {
    final Properties properties = membershipService.getLocalMember().properties();
    properties.setProperty(property, value);
    stats().recordPublishedState(sizeOf(properties));
  }

  /**
   * Applies the gossiped properties of a member.
   *
   * @param onlyKey if not null, only the GLOBAL key with this name is applied
   */
  private void ingest(
      final MemberId owner,
      final Properties properties,
      final @Nullable String onlyKey,
      final List<Runnable> notifications) {
    for (final String property : properties.stringPropertyNames()) {
      final String value = properties.getProperty(property);
      if (property.startsWith(GLOBAL_PREFIX)) {
        final var name = property.substring(GLOBAL_PREFIX.length());
        if (onlyKey == null || onlyKey.equals(name)) {
          mergeRemoteGlobal(name, decodeGlobal(value), notifications);
        }
      } else if (onlyKey == null && property.startsWith(MEMBER_PREFIX)) {
        setMemberValue(
            owner,
            property.substring(MEMBER_PREFIX.length()),
            DECODER.decode(value),
            notifications);
      }
    }
  }

  /** {@code L|<timestamp>|<writer>|<base64>} for last-writer-wins, {@code M|<base64>} otherwise. */
  static String encodeGlobal(final GlobalEntry entry) {
    final var bytes = ENCODER.encodeToString(entry.bytes());
    return entry.lastWriterWins()
        ? "L|" + entry.timestamp() + "|" + entry.writer() + "|" + bytes
        : "M|" + bytes;
  }

  static GlobalEntry decodeGlobal(final String value) {
    if (value.startsWith("M|")) {
      return GlobalEntry.merged(DECODER.decode(value.substring(2)));
    }
    final var parts = value.split("\\|", 4);
    return GlobalEntry.lastWriterWins(
        DECODER.decode(parts[3]), Long.parseLong(parts[1]), MemberId.from(parts[2]));
  }

  private static long sizeOf(final Properties properties) {
    long size = 0;
    for (final String property : properties.stringPropertyNames()) {
      size += property.length() + properties.getProperty(property).length();
    }
    return size;
  }
}
