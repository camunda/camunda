/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.RaftLogReader.Mode;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import java.util.Optional;

public final class AtomixRaftServer implements AtomixReaderFactory, AtomixAppenderSupplier {
  private final RaftPartitionServer server;

  AtomixRaftServer(final RaftPartitionServer server) {
    this.server = server;
  }

  @Override
  public Optional<ZeebeLogAppender> getAppender() {
    return server.getAppender();
  }

  @Override
  public RaftLogReader create(final long index, final Mode mode) {
    return server.openReader(index, mode);
  }
}
