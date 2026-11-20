/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.showcase;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.cluster.gossip.ClusterGossiper;
import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.GossipCodec;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MergeStrategy;
import io.camunda.zeebe.cluster.gossip.Subscription;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Example consumer of {@link ClusterGossiper}, showing both scopes. It is a simplified stand-in for
 * what the topology manager does with member properties today.
 *
 * <ul>
 *   <li>MEMBER key {@link #LEADING_PARTITIONS}: every broker publishes the partitions it currently
 *       leads. Only the broker itself writes its copy, and the copy disappears when the broker
 *       leaves or restarts, so a crashed broker never stays listed as leader.
 *   <li>GLOBAL key {@link #MODE}: any broker can switch the cluster into maintenance mode, and the
 *       last switch wins. The default, {@link Mode#NORMAL}, is never gossiped, so a restarted
 *       broker does not reset the mode the rest of the cluster agreed on.
 * </ul>
 *
 * <p>The view does not know how state travels; it works the same on every gossiper.
 */
public final class ClusterStatusView implements AutoCloseable {

  static final MemberKey<Set<Integer>> LEADING_PARTITIONS =
      GossipKey.member("showcase.leading-partitions", new PartitionSetCodec());

  static final GlobalKey<Mode> MODE =
      GossipKey.global(
          "showcase.mode", new ModeCodec(), MergeStrategy.lastWriterWins(), Mode.NORMAL);

  private final ClusterGossiper gossiper;
  private final Map<MemberId, Set<Integer>> leadingPartitions = new ConcurrentHashMap<>();
  private final Set<Integer> ownPartitions = new HashSet<>();
  private final Subscription partitionsSubscription;
  private final Subscription modeSubscription;
  private volatile Mode mode = Mode.NORMAL;

  public ClusterStatusView(final ClusterGossiper gossiper, final Executor executor) {
    this.gossiper = gossiper;
    partitionsSubscription =
        gossiper.subscribe(LEADING_PARTITIONS, executor, this::onLeadingPartitionsChanged);
    modeSubscription = gossiper.subscribe(MODE, executor, value -> mode = value);
  }

  /** Called on the local broker when it becomes leader of a partition. */
  public synchronized void becameLeader(final int partition) {
    ownPartitions.add(partition);
    gossiper.put(LEADING_PARTITIONS, Set.copyOf(ownPartitions));
  }

  /** Called on the local broker when it stops leading a partition. */
  public synchronized void stoppedLeading(final int partition) {
    ownPartitions.remove(partition);
    gossiper.put(LEADING_PARTITIONS, Set.copyOf(ownPartitions));
  }

  /** Switches the whole cluster into or out of maintenance mode. */
  public void setMode(final Mode mode) {
    gossiper.put(MODE, mode);
  }

  /** The cluster-wide mode, as last seen by this broker. */
  public Mode mode() {
    return mode;
  }

  /**
   * The brokers that claim leadership of a partition. Usually one; briefly two while leadership
   * moves and the old leader's step-down has not arrived yet.
   */
  public Set<MemberId> leadersOf(final int partition) {
    return leadingPartitions.entrySet().stream()
        .filter(entry -> entry.getValue().contains(partition))
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public void close() {
    partitionsSubscription.close();
    modeSubscription.close();
  }

  private void onLeadingPartitionsChanged(
      final MemberId member, final Optional<Set<Integer>> partitions) {
    partitions.ifPresentOrElse(
        value -> leadingPartitions.put(member, value), () -> leadingPartitions.remove(member));
  }

  public enum Mode {
    NORMAL,
    MAINTENANCE
  }

  /** Comma-separated, sorted, so equal sets encode to identical bytes. */
  private static final class PartitionSetCodec implements GossipCodec<Set<Integer>> {

    @Override
    public byte[] encode(final Set<Integer> value) {
      return new TreeSet<>(value)
          .stream()
              .map(String::valueOf)
              .collect(Collectors.joining(","))
              .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Set<Integer> decode(final byte[] bytes) {
      final var text = new String(bytes, StandardCharsets.UTF_8);
      return text.isEmpty()
          ? Set.of()
          : Arrays.stream(text.split(","))
              .map(Integer::valueOf)
              .collect(Collectors.toUnmodifiableSet());
    }
  }

  private static final class ModeCodec implements GossipCodec<Mode> {

    @Override
    public byte[] encode(final Mode value) {
      return value.name().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mode decode(final byte[] bytes) {
      return Mode.valueOf(new String(bytes, StandardCharsets.UTF_8));
    }
  }
}
