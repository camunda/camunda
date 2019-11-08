/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.storage.journal.Indexed;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

public class AtomixLogStorage implements LogStorage {
  private final AtomixReaderFactory readerFactory;
  private final AtomixLogCompactor logCompacter;
  private final AtomixAppenderSupplier appenderSupplier;

  private boolean opened;

  public AtomixLogStorage(
      final AtomixReaderFactory readerFactory,
      final AtomixLogCompactor logCompacter,
      final AtomixAppenderSupplier appenderSupplier) {
    this.readerFactory = readerFactory;
    this.logCompacter = logCompacter;
    this.appenderSupplier = appenderSupplier;
  }

  @Override
  public LogStorageReader newReader() {
    return new AtomixLogStorageReader(readerFactory.create());
  }

  @Override
  public long append(final ByteBuffer blockBuffer) throws IOException {
    throw new UnsupportedOperationException("Not supported");
  }

  @Override
  public CompletableFuture<Long> appendAsync(
      final long lowestPosition, final long highestPosition, final ByteBuffer buffer) {
    final var appender = appenderSupplier.getAppender();

    if (appender.isPresent()) {
      return appender
          .get()
          .appendEntry(lowestPosition, highestPosition, buffer)
          .thenApply(Indexed::index);
    }

    return CompletableFuture.failedFuture(new NoSuchElementException());
  }

  @Override
  public void delete(final long index) {
    logCompacter.compact(index);
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
