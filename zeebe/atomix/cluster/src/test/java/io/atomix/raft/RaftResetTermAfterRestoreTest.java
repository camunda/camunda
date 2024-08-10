/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;

// Regression test https://github.com/camunda/camunda/issues/14509
public class RaftResetTermAfterRestoreTest {
  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(1);

  @Test
  public void shouldResetTermFromLastLogEntryIfMetastoreIsEmpty() throws Exception {
    // given
    raftRule.appendEntries(1);
    final var raftServer = raftRule.getServers().stream().findFirst().get();
    final var serverId =
        raftServer.cluster().getLocalMember().memberId().id(); // There is only one server
    final var termBeforeShutdown = raftServer.getTerm();
    raftRule.shutdownServer(raftServer);
    assertThat(termBeforeShutdown)
        .isEqualTo(1); // We are relying on this assumption for later validation

    // when

    // Simulate the state after restore by deleting metastore
    final var partitionDirectory = raftServer.getContext().getStorage().directory();
    try (final Stream<Path> fileStream = Files.list(partitionDirectory.toPath())) {
      final var metaFilePath =
          fileStream
              .filter(p -> p.getFileName().toString().endsWith("meta"))
              .findFirst()
              .orElseThrow(() -> new RuntimeException("No meta file found"));
      Files.delete(metaFilePath);
    }

    raftRule.joinCluster(serverId);

    // then
    assertThat(raftRule.getLeader().orElseThrow().getTerm())
        .describedAs("Should reset term by reading the last entry in the log")
        .isEqualTo(2);
  }
}
