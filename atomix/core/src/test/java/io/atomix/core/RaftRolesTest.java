/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.core;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public final class RaftRolesTest {

  @Rule public AtomixRule atomixRule = new AtomixRule();

  @Test
  public void testRoleChangedListener() throws Exception {
    // given
    final CompletableFuture<Void> roleChanged = new CompletableFuture<>();

    // when
    final CompletableFuture<Atomix> joinFuture =
        startSingleNodeSinglePartitionWithPartitionConsumer(
            partition -> {
              final RaftPartition raftPartition = (RaftPartition) partition;
              raftPartition.addRoleChangeListener(role -> roleChanged.complete(null));
            });

    // then
    joinFuture.join();
    roleChanged.get();
  }

  @Test
  public void testExceptionInRoleChangedListener() throws Exception {
    // given
    final CompletableFuture<Void> roleChanged = new CompletableFuture<>();

    final CompletableFuture<Atomix> joinFuture =
        startSingleNodeSinglePartitionWithPartitionConsumer(
            partition -> {
              final RaftPartition raftPartition = (RaftPartition) partition;
              raftPartition.addRoleChangeListener(
                  role -> {
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
                  role -> {
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
    final CompletableFuture<Atomix> nodeOneFuture =
        startAtomixWithPartitionConsumer(
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
    final CompletableFuture<Atomix> nodeTwoFuture =
        startAtomixWithPartitionConsumer(
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
    final CompletableFuture<Atomix> nodeThreeFuture =
        startAtomixWithPartitionConsumer(
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

  private CompletableFuture<Atomix> startSingleNodeSinglePartitionWithPartitionConsumer(
      final Consumer<? super Partition> partitionConsumer) {
    return startAtomixSinglePartitionWithPartitionConsumer(
        1, Collections.singletonList(1), partitionConsumer);
  }

  private CompletableFuture<Atomix> startAtomixSinglePartitionWithPartitionConsumer(
      final int nodeId,
      final List<Integer> nodeIds,
      final Consumer<? super Partition> partitionConsumer) {

    return startAtomixWithPartitionConsumer(nodeId, 1, nodeIds, partitionConsumer);
  }

  private CompletableFuture<Atomix> startAtomixWithPartitionConsumer(
      final int nodeId,
      final int partitionCount,
      final List<Integer> nodeIds,
      final Consumer<? super Partition> partitionConsumer) {

    final List<String> memberIds =
        nodeIds.stream().map(Object::toString).collect(Collectors.toList());

    return atomixRule.startAtomix(
        nodeId,
        nodeIds,
        builder -> {
          final RaftPartitionGroup partitionGroup =
              RaftPartitionGroup.builder("normal")
                  .withNumPartitions(partitionCount)
                  .withPartitionSize(memberIds.size())
                  .withMembers(memberIds)
                  .withDataDirectory(
                      new File(new File(atomixRule.getDataDir(), "log"), "" + nodeId))
                  .withSnapshotStoreFactory(new NoopSnapshotStoreFactory())
                  .build();

          final Atomix atomix = builder.withPartitionGroups(partitionGroup).build();

          final DefaultPartitionService partitionService =
              (DefaultPartitionService) atomix.getPartitionService();

          partitionService.getPartitionGroups().stream()
              .findFirst()
              .ifPresent(
                  raftPartitionGroup ->
                      raftPartitionGroup.getPartitions().forEach(partitionConsumer));
          return atomix;
        });
  }
}
