/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.AtomixClusterBuilder;
import io.atomix.cluster.AtomixClusterRule;
import io.atomix.cluster.NoopSnapshotStoreFactory;
import io.atomix.primitive.partition.Partition;
import io.atomix.primitive.partition.impl.DefaultPartitionService;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionGroup;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.agrona.LangUtil;
import org.junit.Rule;
import org.junit.Test;

public final class RaftRolesTest {

  @Rule public AtomixClusterRule atomixClusterRule = new AtomixClusterRule();

  @Test
  public void testRoleChangedListener() throws Exception {
    // given
    final CompletableFuture<Void> roleChanged = new CompletableFuture<>();

    // when
    final CompletableFuture<Void> joinFuture =
        startSingleNodeSinglePartitionWithPartitionConsumer(
            partition -> {
              final RaftPartition raftPartition = (RaftPartition) partition;
              raftPartition.addRoleChangeListener((role, term) -> roleChanged.complete(null));
            });

    // then
    joinFuture.join();
    roleChanged.get();
  }

  @Test
  public void testExceptionInRoleChangedListener() throws Exception {
    // given
    final CompletableFuture<Void> roleChanged = new CompletableFuture<>();

    final CompletableFuture<Void> joinFuture =
        startSingleNodeSinglePartitionWithPartitionConsumer(
            partition -> {
              final RaftPartition raftPartition = (RaftPartition) partition;
              raftPartition.addRoleChangeListener(
                  (role, term) -> {
                    roleChanged.complete(null);

                    // when
                    throw new RuntimeException("expected");
                  });
            });

    // then
    joinFuture.join();
    roleChanged.get(60, TimeUnit.SECONDS);
  }

  @Test
  public void testStepDownInRoleChangedListener() throws Exception {
    // given
    final CompletableFuture<Void> roleChanged = new CompletableFuture<>();
    final CountDownLatch followerLatch = new CountDownLatch(2);
    final List<Role> roles = new ArrayList<>();

    startSingleNodeSinglePartitionWithPartitionConsumer(
            partition -> {
              final RaftPartition raftPartition = (RaftPartition) partition;
              raftPartition.addRoleChangeListener(
                  (role, term) -> {
                    roles.add(role);
                    if (!roleChanged.isDone() && role == Role.LEADER) {
                      roleChanged.complete(null);

                      // when
                      raftPartition.stepDown();
                    } else if (role == Role.FOLLOWER) {
                      followerLatch.countDown();
                    }
                  });
            })
        .join();

    // then
    roleChanged.get(60, TimeUnit.SECONDS);
    followerLatch.await(10, TimeUnit.SECONDS);

    // single node becomes directly leader again
    assertThat(roles).containsSequence(Role.INACTIVE, Role.LEADER, Role.LEADER);
  }

  private CompletableFuture<Void> startSingleNodeSinglePartitionWithPartitionConsumer(
      final Consumer<? super Partition> partitionConsumer) {

    return startPartitionManagerSinglePartitionWithPartitionConsumer(
        1, Collections.singletonList(1), partitionConsumer);
  }

  private CompletableFuture<Void> startPartitionManagerSinglePartitionWithPartitionConsumer(
      final int nodeId,
      final List<Integer> nodeIds,
      final Consumer<? super Partition> partitionConsumer) {

    return startPartitionManagerWithPartitionConsumer(nodeId, 1, nodeIds, partitionConsumer);
  }

  private CompletableFuture<Void> startPartitionManagerWithPartitionConsumer(
      final int nodeId,
      final int partitionCount,
      final List<Integer> nodeIds,
      final Consumer<? super Partition> partitionConsumer) {

    final List<String> memberIds =
        nodeIds.stream().map(Object::toString).collect(Collectors.toList());

    final RaftPartitionGroup partitionGroup =
        RaftPartitionGroup.builder("normal")
            .withNumPartitions(partitionCount)
            .withPartitionSize(memberIds.size())
            .withPriorityElection(false)
            .withMembers(memberIds)
            .withDataDirectory(
                new File(new File(atomixClusterRule.getDataDir(), "log"), "" + nodeId))
            .withSnapshotStoreFactory(new NoopSnapshotStoreFactory())
            .build();

    final var atomixFuture =
        atomixClusterRule.startAtomix(nodeId, nodeIds, AtomixClusterBuilder::build);

    final AtomixCluster atomix;
    try {
      atomix = atomixFuture.get();

      final var partitionService =
          new DefaultPartitionService(
              atomix.getMembershipService(), atomix.getCommunicationService(), partitionGroup);

      partitionGroup.getPartitions().forEach(partitionConsumer);

      return partitionService.start().thenApply(ps -> null);
    } catch (final InterruptedException | ExecutionException e) {
      LangUtil.rethrowUnchecked(e);
      // won't be executed
      return null;
    }
  }
}
