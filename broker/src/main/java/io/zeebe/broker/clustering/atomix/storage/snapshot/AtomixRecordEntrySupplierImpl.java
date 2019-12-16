/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.zeebe.broker.clustering.atomix.storage.AtomixRecordEntrySupplier;
import io.zeebe.logstreams.storage.atomix.AtomixLogStorageReader;
import java.util.Optional;

public final class AtomixRecordEntrySupplierImpl implements AtomixRecordEntrySupplier {
  private final AtomixLogStorageReader reader;

  public AtomixRecordEntrySupplierImpl(final AtomixLogStorageReader reader) {
    this.reader = reader;
  }

  @Override
  public Optional<Indexed<ZeebeEntry>> getIndexedEntry(final long position) {
    final var index = reader.lookUpApproximateAddress(position);
    // since Atomix assumes that a snapshot for index Y means Y is processed, return for the
    // previous index
    return reader.findEntry(index - 1);
  }

  @Override
  public void close() {
    reader.close();
  }
}
