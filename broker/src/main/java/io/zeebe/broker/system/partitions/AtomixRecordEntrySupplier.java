/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions;

import io.atomix.raft.storage.log.Indexed;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.zeebe.ZeebeEntry;
import java.util.Optional;

/**
 * Implementations of this interface should provide the correct {@link Indexed<ZeebeEntry>} when
 * given a Record#getPosition()
 */
@FunctionalInterface
public interface AtomixRecordEntrySupplier extends AutoCloseable {
  Optional<Indexed<RaftLogEntry>> getIndexedEntry(long position);

  @Override
  default void close() {}
}
