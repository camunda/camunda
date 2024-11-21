/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftRule.Configurator;
import io.atomix.raft.RaftServer.Builder;
import io.atomix.raft.partition.RaftElectionConfig;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.RaftLogFlusher;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.Journal;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Set the first faultyFlusherNumber nodes with a faulty flusher, when the supplier returns true */
public record FaultyFlusherConfigurator(
    int faultyFlusherNumber,
    Supplier<Boolean> faultyWhen,
    Runnable notifyFaultyFlush,
    boolean leaderFaulty,
    boolean withDataLoss)
    implements Configurator {

  private static final Logger LOG = LoggerFactory.getLogger(FaultyFlusherConfigurator.class);

  private RaftLogFlusher.Factory faultyFlusher(
      final Supplier<Boolean> faultyWhen, final Runnable notifyFaultyFlush) {
    return (ignored) ->
        new RaftLogFlusher() {
          @Override
          public void flush(final Journal journal) throws FlushException {
            if (faultyWhen.get()) {
              notifyFaultyFlush.run();
              if (withDataLoss) {
                journal.deleteAfter(journal.getLastIndex() - 1);
              }
              throw new FlushException(new IOException("Failed sync"));
            } else {
              journal.flush();
            }
          }
        };
  }

  @Override
  public void configure(final MemberId id, final Builder builder) {
    final var numericId = Integer.parseInt(id.id());
    // Node priority is used to avoid the faulty nodes to become leaders
    final int nodePriority;
    if (numericId <= faultyFlusherNumber) {
      LOG.trace("failing flusher for member {}", id);
      final var storage = builder.storage;
      Objects.requireNonNull(storage);
      builder.withStorage(
          RaftStorage.builder()
              .withDirectory(storage.directory())
              .withSnapshotStore(storage.getPersistedSnapshotStore())
              .withFlusherFactory(faultyFlusher(faultyWhen, notifyFaultyFlush))
              .build());
      nodePriority = leaderFaulty ? Math.max(5 - numericId, 2) : numericId;
    } else {
      LOG.trace("not failing flusher for member {} ", id);
      nodePriority = leaderFaulty ? numericId : Math.max(5 - numericId, 2);
    }
    builder.withElectionConfig(RaftElectionConfig.ofPriorityElection(5, nodePriority));
  }
}
