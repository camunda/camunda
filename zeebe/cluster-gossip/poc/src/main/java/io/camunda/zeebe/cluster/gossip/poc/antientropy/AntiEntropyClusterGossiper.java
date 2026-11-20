/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.antientropy;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.cluster.gossip.poc.antientropy.GossipMessage.GlobalItem;
import io.camunda.zeebe.cluster.gossip.poc.antientropy.GossipMessage.MemberItem;
import io.camunda.zeebe.cluster.gossip.poc.antientropy.GossipMessage.RunItem;
import io.camunda.zeebe.cluster.gossip.poc.core.AbstractClusterGossiper;
import io.camunda.zeebe.cluster.gossip.poc.core.GlobalEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proof of concept: a gossiper with its own transport over {@link ClusterCommunicationService}
 * (TCP), using SWIM only to know who the members are.
 *
 * <ul>
 *   <li><b>Push (rumor mongering):</b> changes are collected and sent as a delta to a few random
 *       peers in each of the next few push rounds. A peer whose state changes forwards the change
 *       the same way; a peer that already had it does not, so the rumor dies out.
 *   <li><b>Pull (anti-entropy):</b> every sync interval the full state is exchanged with one random
 *       peer, in both directions. This re-delivers anything a push missed, including values dropped
 *       because their key was not registered yet, and catches up members returning from a
 *       partition.
 * </ul>
 *
 * <p>MEMBER values carry the owner's {@link Run} and a per-run version. A newer run replaces every
 * value of the older run, and each member announces its run on start, so a restart that SWIM never
 * reports as a removal still drops the old values. When SWIM removes a member, its values are
 * dropped and its run remembered, so that stale copies still travelling cannot bring them back.
 *
 * <p>All network handling runs on one scheduler thread per gossiper.
 */
public final class AntiEntropyClusterGossiper extends AbstractClusterGossiper
    implements ClusterMembershipEventListener {

  static final String PUSH_SUBJECT = "cluster-gossip-push";
  static final String SYNC_SUBJECT = "cluster-gossip-sync";
  private static final Logger LOG = LoggerFactory.getLogger(AntiEntropyClusterGossiper.class);

  private final ClusterCommunicationService communicationService;
  private final ClusterMembershipService membershipService;
  private final AntiEntropyConfig config;
  private final ScheduledExecutorService executor;
  private final Run localRun;

  // all guarded by lock
  private final Map<MemberId, Run> runs = new HashMap<>();
  private final Map<MemberId, Map<String, Long>> versions = new HashMap<>();
  private final Map<MemberId, Run> removedRuns = new HashMap<>();
  // changes still to push, with the number of pushes left for each
  private final Map<String, Integer> dirtyGlobals = new HashMap<>();
  private final Map<MemberValueId, Integer> dirtyMembers = new HashMap<>();
  private final Map<MemberId, Integer> dirtyRuns = new HashMap<>();
  private long nextVersion;

  public AntiEntropyClusterGossiper(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService membershipService,
      final AntiEntropyConfig config) {
    this(communicationService, membershipService, config, System::currentTimeMillis);
  }

  public AntiEntropyClusterGossiper(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService membershipService,
      final AntiEntropyConfig config,
      final LongSupplier clock) {
    super(membershipService.getLocalMember().id(), clock);
    this.communicationService = communicationService;
    this.membershipService = membershipService;
    this.config = config;
    localRun = new Run(clock.getAsLong(), ThreadLocalRandom.current().nextLong());
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, "cluster-gossip-" + localMemberId);
              thread.setDaemon(true);
              return thread;
            });
  }

  @Override
  public void start() {
    synchronized (lock) {
      runs.put(localMemberId, localRun);
      dirtyRuns.put(localMemberId, config.retransmits());
    }
    communicationService.consume(PUSH_SUBJECT, Function.identity(), this::onPush, executor);
    communicationService.replyTo(
        SYNC_SUBJECT, Function.identity(), this::onSyncRequest, this::encodeCounted, executor);
    membershipService.addListener(this);
    executor.scheduleWithFixedDelay(
        this::push, 0, config.pushInterval().toMillis(), TimeUnit.MILLISECONDS);
    executor.scheduleWithFixedDelay(
        this::sync, 0, config.syncInterval().toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    membershipService.removeListener(this);
    communicationService.unsubscribe(PUSH_SUBJECT);
    communicationService.unsubscribe(SYNC_SUBJECT);
    executor.shutdownNow();
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    if (event.type() != Type.MEMBER_REMOVED) {
      return;
    }
    final var owner = event.subject().id();
    withLock(
        notifications -> {
          // SWIM reports a restart as MEMBER_REMOVED immediately followed by MEMBER_ADDED, and
          // does not expose which run it saw. If the member is back already, its own run
          // announcement replaces the old values; only a member that is really gone is dropped.
          if (owner.equals(localMemberId) || membershipService.getMember(owner) != null) {
            return;
          }
          final var run = runs.remove(owner);
          if (run != null) {
            removedRuns.put(owner, run);
          }
          versions.remove(owner);
          removeMemberValues(owner, notifications);
        });
  }

  @Override
  protected void onGlobalChanged(final String name, final GlobalEntry entry) {
    dirtyGlobals.put(name, config.retransmits());
  }

  @Override
  protected void onLocalMemberValueChanged(final String name, final byte[] bytes) {
    versions.computeIfAbsent(localMemberId, ignored -> new HashMap<>()).put(name, ++nextVersion);
    dirtyMembers.put(new MemberValueId(localMemberId, name), config.retransmits());
  }

  private void push() {
    try {
      final var peers = randomPeers(config.fanout());
      if (peers.isEmpty()) {
        // keep the changes until a peer is known, e.g. right after start
        return;
      }
      final GossipMessage delta;
      synchronized (lock) {
        delta = buildMessage(dirtyRuns.keySet(), dirtyGlobals.keySet(), dirtyMembers.keySet());
        countDown(dirtyRuns);
        countDown(dirtyGlobals);
        countDown(dirtyMembers);
      }
      if (delta.isEmpty()) {
        return;
      }
      for (final var peer : peers) {
        final var bytes = encodeCounted(delta);
        communicationService.unicast(PUSH_SUBJECT, bytes, Function.identity(), peer, true);
      }
    } catch (final RuntimeException e) {
      LOG.warn("Failed to push gossip changes", e);
    }
  }

  private static <K> void countDown(final Map<K, Integer> pending) {
    pending.replaceAll((key, left) -> left - 1);
    pending.values().removeIf(left -> left <= 0);
  }

  private void sync() {
    try {
      final var peers = randomPeers(1);
      if (peers.isEmpty()) {
        return;
      }
      final GossipMessage state;
      synchronized (lock) {
        state = fullState();
      }
      final var peer = peers.getFirst();
      communicationService
          .<byte[], byte[]>send(
              SYNC_SUBJECT,
              encodeCounted(state),
              Function.identity(),
              Function.identity(),
              peer,
              config.syncTimeout())
          .thenAcceptAsync(reply -> receive(GossipMessage.decode(reply)), executor)
          .exceptionally(
              error -> {
                LOG.debug("Failed to sync gossip state with {}", peer, error);
                return null;
              });
    } catch (final RuntimeException e) {
      LOG.warn("Failed to sync gossip state", e);
    }
  }

  private void onPush(final MemberId sender, final byte[] payload) {
    receive(GossipMessage.decode(payload));
  }

  private GossipMessage onSyncRequest(final MemberId sender, final byte[] payload) {
    receive(GossipMessage.decode(payload));
    synchronized (lock) {
      return fullState();
    }
  }

  private void receive(final GossipMessage message) {
    withLock(
        notifications -> {
          for (final var item : message.runs()) {
            adoptRun(item.owner(), item.run(), notifications);
          }
          for (final var item : message.globals()) {
            mergeRemoteGlobal(
                item.name(),
                new GlobalEntry(
                    item.bytes(), item.lastWriterWins(), item.timestamp(), item.writer()),
                notifications);
          }
          for (final var item : message.members()) {
            receiveMemberValue(item, notifications);
          }
        });
  }

  /** Returns whether values of this run of the owner are accepted. */
  private boolean adoptRun(
      final MemberId owner, final Run run, final List<Runnable> notifications) {
    if (owner.equals(localMemberId) || membershipService.getMember(owner) == null) {
      // not a member (yet): the next sync delivers its values again
      return false;
    }
    final var removed = removedRuns.get(owner);
    if (removed != null && !run.isNewerThan(removed)) {
      return false;
    }
    final var current = runs.get(owner);
    if (current != null && !run.isNewerThan(current)) {
      return run.equals(current);
    }
    runs.put(owner, run);
    versions.remove(owner);
    removeMemberValues(owner, notifications);
    dirtyRuns.put(owner, config.retransmits());
    return true;
  }

  private void receiveMemberValue(final MemberItem item, final List<Runnable> notifications) {
    if (!adoptRun(item.owner(), item.run(), notifications)) {
      return;
    }
    final var ownerVersions = versions.computeIfAbsent(item.owner(), ignored -> new HashMap<>());
    final var current = ownerVersions.get(item.name());
    if (current != null && current >= item.version()) {
      return;
    }
    ownerVersions.put(item.name(), item.version());
    setMemberValue(item.owner(), item.name(), item.bytes(), notifications);
    dirtyMembers.put(new MemberValueId(item.owner(), item.name()), config.retransmits());
  }

  private GossipMessage fullState() {
    final var allMembers = new HashSet<MemberValueId>();
    memberValues()
        .forEach(
            (owner, values) ->
                values.keySet().forEach(name -> allMembers.add(new MemberValueId(owner, name))));
    return buildMessage(runs.keySet(), globals().keySet(), allMembers);
  }

  private GossipMessage buildMessage(
      final Set<MemberId> runOwners, final Set<String> globalNames, final Set<MemberValueId> ids) {
    final var runItems = new ArrayList<RunItem>();
    for (final var owner : runOwners) {
      final var run = runs.get(owner);
      if (run != null) {
        runItems.add(new RunItem(owner, run));
      }
    }
    final var globalItems = new ArrayList<GlobalItem>();
    for (final var name : globalNames) {
      final var entry = globals().get(name);
      if (entry != null) {
        globalItems.add(
            new GlobalItem(
                name, entry.lastWriterWins(), entry.timestamp(), entry.writer(), entry.bytes()));
      }
    }
    final var memberItems = new ArrayList<MemberItem>();
    for (final var id : ids) {
      final var run = runs.get(id.owner());
      final var version = versions.getOrDefault(id.owner(), Map.of()).get(id.name());
      final var bytes = memberValues().getOrDefault(id.owner(), Map.of()).get(id.name());
      if (run != null && version != null && bytes != null) {
        memberItems.add(new MemberItem(id.owner(), run, id.name(), version, bytes));
      }
    }
    return new GossipMessage(runItems, globalItems, memberItems);
  }

  private List<MemberId> randomPeers(final int count) {
    final var peers = new ArrayList<MemberId>();
    for (final Member member : membershipService.getMembers()) {
      if (!member.id().equals(localMemberId) && member.isReachable()) {
        peers.add(member.id());
      }
    }
    Collections.shuffle(peers);
    return peers.subList(0, Math.min(count, peers.size()));
  }

  private byte[] encodeCounted(final GossipMessage message) {
    final var bytes = message.encode();
    stats().recordMessage(bytes.length);
    return bytes;
  }

  private record MemberValueId(MemberId owner, String name) {}
}
