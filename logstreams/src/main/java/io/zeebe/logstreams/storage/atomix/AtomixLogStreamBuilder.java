/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.protocols.raft.partition.RaftPartition;
import io.zeebe.logstreams.impl.LogStreamBuilder;

public class AtomixLogStreamBuilder extends LogStreamBuilder<AtomixLogStreamBuilder> {
  private RaftPartition partition;

  public AtomixLogStreamBuilder withPartition(final RaftPartition partition) {
    this.partition = partition;
    return this;
  }

  @Override
  protected void applyDefaults() {
    if (partition != null) {
      if (logStorage == null) {
        final var server = new AtomixRaftServer(partition.getServer());
        logStorage = new AtomixLogStorage(server, server, server);
      }

      if (partitionId < 0) {
        partitionId = partition.id().id();
      }

      if (logName == null) {
        logName = partition.name();
      }
    }
  }
}
