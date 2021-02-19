/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.raft.partition.RaftPartition;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;

public class AtomixLogStorage implements LogStorage {
  private final AtomixReaderFactory readerFactory;
  private final AtomixAppenderSupplier appenderSupplier;

  private boolean opened;

  public AtomixLogStorage(
      final AtomixReaderFactory readerFactory, final AtomixAppenderSupplier appenderSupplier) {
    this.readerFactory = readerFactory;
    this.appenderSupplier = appenderSupplier;
  }

  public static AtomixLogStorage ofPartition(final RaftPartition partition) {
    final var server = new AtomixRaftServer(partition.getServer());
    return new AtomixLogStorage(server, server);
  }

  @Override
  public LogStorageReader newReader() {
    return new AtomixLogStorageReader(readerFactory.create());
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final ByteBuffer buffer,
      final AppendListener listener) {
    final var optionalAppender = appenderSupplier.getAppender();

    if (optionalAppender.isPresent()) {
      final var appender = optionalAppender.get();
      final var adapter = new AtomixAppendListenerAdapter(listener);
      appender.appendEntry(lowestPosition, highestPosition, buffer, adapter);
    } else {
      // todo: better error message
      listener.onWriteError(
          new NoSuchElementException(
              "Expected an appender, but none found, most likely we are not the leader"));
    }
  }

  @Override
  public void open() {
    opened = true;
  }

  @Override
  public void close() {
    opened = false;
  }

  @Override
  public boolean isOpen() {
    return opened;
  }

  @Override
  public boolean isClosed() {
    return !opened;
  }

  @Override
  public void flush() throws Exception {
    // does nothing as append guarantees blocks are appended immediately
  }
}
