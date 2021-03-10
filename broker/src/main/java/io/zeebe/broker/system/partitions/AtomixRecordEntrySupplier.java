/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions;

import io.atomix.raft.storage.log.Indexed;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import java.util.Optional;

/**
 * Implementations of this interface should return the previous {@link Indexed<RaftLogEntry>} of a
 * RaftLogEntry that contains a {@link io.atomix.raft.zeebe.ZeebeEntry} with the given position.
 */
@FunctionalInterface
public interface AtomixRecordEntrySupplier extends AutoCloseable {
  Optional<Indexed<RaftLogEntry>> getPreviousIndexedEntry(long position);

  @Override
  default void close() {}
}
