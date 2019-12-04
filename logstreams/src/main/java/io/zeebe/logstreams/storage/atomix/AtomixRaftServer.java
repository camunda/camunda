/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.protocols.raft.partition.impl.RaftPartitionServer;
import io.atomix.protocols.raft.storage.log.RaftLogReader;
import io.atomix.protocols.raft.zeebe.ZeebeLogAppender;
import io.atomix.storage.journal.JournalReader.Mode;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AtomixRaftServer
    implements AtomixLogCompactor, AtomixReaderFactory, AtomixAppenderSupplier {
  private final RaftPartitionServer server;

  AtomixRaftServer(final RaftPartitionServer server) {
    this.server = server;
  }

  @Override
  public Optional<ZeebeLogAppender> getAppender() {
    return server.getAppender();
  }

  @Override
  public CompletableFuture<Void> compact(final long index) {
    server.setCompactableIndex(index);
    return server.snapshot();
  }

  @Override
  public RaftLogReader create(final long index, final Mode mode) {
    return server.openReader(index, mode);
  }
}
