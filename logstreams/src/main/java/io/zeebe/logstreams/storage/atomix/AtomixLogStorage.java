/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import java.nio.ByteBuffer;

public class AtomixLogStorage implements LogStorage {
  private final AtomixReaderFactory readerFactory;

  private boolean opened;
  private final ZeebeLogAppender logAppender;

  public AtomixLogStorage(
      final AtomixReaderFactory readerFactory, final ZeebeLogAppender logAppender) {
    this.readerFactory = readerFactory;
    this.logAppender = logAppender;
  }

  public static AtomixLogStorage ofPartition(
      final AtomixReaderFactory readerFactory, final ZeebeLogAppender appender) {
    return new AtomixLogStorage(readerFactory, appender);
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
    final var adapter = new AtomixAppendListenerAdapter(listener);
    logAppender.appendEntry(lowestPosition, highestPosition, buffer, adapter);
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
