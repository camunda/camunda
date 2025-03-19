/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.*;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

public class RaftCorruptedDataTest {
  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  @DisplayName(
      "When nodes with corrupted data forms a quorum, the remaining one should not delete its files")
  public void upToDateFollowerShouldNotLoseDataWhenQuorumExperienceCorruption() throws Exception {
    // given
    // nodes 0, 1 experience corruption: all data is lost
    final var servers = raftRule.getServers().toArray(RaftServer[]::new);
    // name == memberId since the name is not specified
    final var node0 = MemberId.from(servers[0].name());
    final var node1 = MemberId.from(servers[1].name());
    final var node2 = MemberId.from(servers[2].name());

    raftRule.appendEntries(100);
    raftRule.appendEntries(1);
    Awaitility.await("commitIndex is > 0 on all nodes")
        .until(() -> Arrays.stream(servers).allMatch(s -> s.getContext().getCommitIndex() >= 100));

    final var commitIndex = servers[2].getContext().getCommitIndex();

    for (final RaftServer server : servers) {
      raftRule.shutdownServer(server);
    }

    raftRule.triggerDataLossOnNode(node0.id());
    raftRule.triggerDataLossOnNode(node1.id());

    // after shutdown the servers must be created from scratch with the same server name.
    final var server0 = raftRule.createServer(node0);
    final var server1 = raftRule.createServer(node1);

    // bootstrap the nodes with data loss
    CompletableFuture.allOf(
            server0.bootstrap(node0, node1, node2), server1.bootstrap(node0, node1, node2))
        .join();

    // wait for the two corrupted nodes to form quorum
    Awaitility.await("corrupted nodes form a quorum")
        .until(() -> server0.isLeader() || server1.isLeader());
    // when
    // the node with up-to-date log joins the cluster
    final var server2 = raftRule.createServer(node2);

    // then
    // node2 fails to boostrap, because it detects the commitIndex mismatch
    assertThatThrownBy(() -> server2.bootstrap(node0, node1, node2).get(2, TimeUnit.SECONDS))
        .isExactlyInstanceOf(TimeoutException.class);

    // node does not become follower
    Awaitility.await("node becomes INACTIVE").until(() -> server2.getRole().equals(Role.INACTIVE));
  }
}
