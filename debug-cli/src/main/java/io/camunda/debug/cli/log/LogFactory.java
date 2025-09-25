/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.log;

import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.camunda.debug.cli.Main;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.CloseHelper;

public class LogFactory implements AutoCloseable {
  private static final String PARTITION_NAME_FORMAT = "raft-partition-partition-%d";

  private final Map<Path, RaftLog> logs = new ConcurrentHashMap<>();

  public RaftLogReader newReader(final Path logPath) {
    // CLOSE IT!
    return logs.computeIfAbsent(logPath, this::buildLog).openUncommittedReader();
  }

  private RaftLog buildLog(final Path logPath) {

    final var partitionId = Integer.parseInt(logPath.getFileName().toString());
    final var raftStorage =
        RaftStorage.builder(Main.meterRegistry)
            .withDirectory(logPath.toFile())
            .withPrefix(String.format(PARTITION_NAME_FORMAT, partitionId))
            .withPartitionId(partitionId)
            .build();
    return raftStorage.openLog(
        new NoopMetaStore(), () -> new SingleThreadContext("debug-cli-raft-context-%d"));
  }

  @Override
  public void close() throws Exception {
    CloseHelper.closeAll(logs.values());
  }
}
