/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.AtomixClusterBuilder;
import io.atomix.cluster.AtomixClusterRule;
import io.atomix.cluster.NoopSnapshotStoreFactory;
import io.atomix.primitive.partition.Partition;
import io.atomix.primitive.partition.impl.DefaultPartitionService;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionGroup;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    assertThat(roles, contains(Role.LEADER, Role.LEADER));
  }

  @Test
  public void testStepDownOnRoleChangeInCluster() throws Exception {
    // given
    // normal distribution
    // Partitions \ Nodes
    //      \   0  1  2
    //    0     L  F  F
    //    1     F  L  F
    //    2     F  F  L
    final CountDownLatch latch = new CountDownLatch(3);
    final List<Map<Integer, Role>> nodeRoles = new CopyOnWriteArrayList<>();
    nodeRoles.add(new ConcurrentHashMap<>());
    nodeRoles.add(new ConcurrentHashMap<>());
    nodeRoles.add(new ConcurrentHashMap<>());

    final List<Integer> members = Arrays.asList(1, 2, 3);

    final int firstNodeId = 1;
    final int expectedFirstNodeLeadingPartition = 1;
    final CompletableFuture<Void> nodeOneFuture =
        startPartitionManagerWithPartitionConsumer(
            firstNodeId,
            3,
            members,
            partition -> {
              final Map<Integer, RaftServer.Role> roleMap = nodeRoles.get(0);
              final RaftPartition raftPartition = (RaftPartition) partition;
              raftPartition.addRoleChangeListener(
                  (role, term) -> {
                    final Integer partitionId = partition.id().id();
                    roleMap.put(partitionId, role);

                    if (role == Role.LEADER) {
                      if (partitionId == expectedFirstNodeLeadingPartition) {
                        raftPartition.stepDown();
                      } else {
                        latch.countDown();
                      }
                    }
                  });
            });

    final int secondNodeId = 2;
    final int expectedSecondNodeLeadingPartition = 2;
    final CompletableFuture<Void> nodeTwoFuture =
        startPartitionManagerWithPartitionConsumer(
            secondNodeId,
            3,
            members,
            partition -> {
              final Map<Integer, RaftServer.Role> roleMap = nodeRoles.get(1);
              final RaftPartition raftPartition = (RaftPartition) partition;
              raftPartition.addRoleChangeListener(
                  (role, term) -> {
                    final Integer partitionId = partition.id().id();
                    roleMap.put(partitionId, role);

                    if (role == Role.LEADER) {
                      if (partitionId == expectedSecondNodeLeadingPartition) {
                        raftPartition.stepDown();
                      } else {
                        latch.countDown();
                      }
                    }
                  });
            });

    final int thirdNodeId = 3;
    final int expectedThirdNodeLeadingPartition = 3;
    final CompletableFuture<Void> nodeThreeFuture =
        startPartitionManagerWithPartitionConsumer(
            thirdNodeId,
            3,
            members,
            partition -> {
              final Map<Integer, RaftServer.Role> roleMap = nodeRoles.get(2);
              final RaftPartition raftPartition = (RaftPartition) partition;
              raftPartition.addRoleChangeListener(
                  (role, term) -> {
                    final Integer partitionId = partition.id().id();
                    roleMap.put(partitionId, role);

                    if (role == Role.LEADER) {
                      if (partitionId == expectedThirdNodeLeadingPartition) {
                        raftPartition.stepDown();
                      } else {
                        latch.countDown();
                      }
                    }
                  });
            });

    // then
    CompletableFuture.allOf(nodeOneFuture, nodeTwoFuture, nodeThreeFuture).join();
    assertTrue(latch.await(15, TimeUnit.SECONDS));

    // expect normal leaders are not the leaders this time
    assertEquals(Role.FOLLOWER, nodeRoles.get(0).get(1));
    assertEquals(Role.FOLLOWER, nodeRoles.get(1).get(2));
    assertEquals(Role.FOLLOWER, nodeRoles.get(2).get(3));

    final List<Role> leaderRoles =
        nodeRoles.stream()
            .flatMap(map -> map.values().stream())
            .filter(r -> r == Role.LEADER)
            .collect(Collectors.toList());

    final List<Role> followerRoles =
        nodeRoles.stream()
            .flatMap(map -> map.values().stream())
            .filter(r -> r == Role.FOLLOWER)
            .collect(Collectors.toList());

    assertEquals(3, leaderRoles.size());
    assertEquals(6, followerRoles.size());
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
