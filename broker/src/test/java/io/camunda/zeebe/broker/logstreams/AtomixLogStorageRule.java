/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.logstreams;

import static org.mockito.Mockito.spy;

import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.system.MetaStore;
import io.atomix.raft.zeebe.EntryValidator;
import io.atomix.raft.zeebe.ValidationResult;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

public final class AtomixLogStorageRule extends ExternalResource
    implements AtomixReaderFactory, ZeebeLogAppender, Supplier<LogStorage> {

  private final TemporaryFolder temporaryFolder;
  private final int partitionId;
  private final UnaryOperator<RaftStorage.Builder> builder;

  private RaftStorage raftStorage;
  private RaftLog raftLog;
  private MetaStore metaStore;

  private AtomixLogStorage storage;
  private LongConsumer positionListener;
  private Consumer<Throwable> writeErrorListener;
  private final EntryValidator entryValidator;

  public AtomixLogStorageRule(
      final TemporaryFolder temporaryFolder,
      final int partitionId,
      final EntryValidator entryValidator) {
    this(temporaryFolder, partitionId, UnaryOperator.identity(), entryValidator);
  }

  public AtomixLogStorageRule(
      final TemporaryFolder temporaryFolder,
      final int partitionId,
      final UnaryOperator<RaftStorage.Builder> builder,
      final EntryValidator entryValidator) {
    this.temporaryFolder = temporaryFolder;
    this.partitionId = partitionId;
    this.builder = builder;
    this.entryValidator = entryValidator;
  }

  @Override
  public void before() {
    open();
  }

  @Override
  public void after() {
    close();
  }

  @Override
  public void appendEntry(
      final long lowestPosition,
      final long highestPosition,
      final ByteBuffer data,
      final AppendListener listener) {
    final ApplicationEntry zbEntry = new ApplicationEntry(lowestPosition, highestPosition, data);
    final IndexedRaftLogEntry lastEntry = raftLog.getLastEntry();

    ApplicationEntry lastZbEntry = null;
    if (lastEntry != null && lastEntry.isApplicationEntry()) {
      lastZbEntry = lastEntry.getApplicationEntry();
    }

    final ValidationResult result = entryValidator.validateEntry(lastZbEntry, zbEntry);
    if (result.failed()) {
      final Throwable exception = new IllegalStateException(result.getErrorMessage());
      listener.onWriteError(exception);
      if (writeErrorListener != null) {
        writeErrorListener.accept(exception);
      }

      return;
    }

    final IndexedRaftLogEntry entry = raftLog.append(new RaftLogEntry(1, zbEntry));

    listener.onWrite(entry);
    raftLog.setCommitIndex(entry.index());

    listener.onCommit(entry);
    storage.onCommit(entry.index());
    if (positionListener != null) {
      positionListener.accept(highestPosition);
    }
  }

  @Override
  public AtomixLogStorage get() {
    return storage;
  }

  @Override
  public RaftLogReader create() {
    return raftLog.openUncommittedReader();
  }

  public void setPositionListener(final LongConsumer positionListener) {
    this.positionListener = positionListener;
  }

  public void setWriteErrorListener(final Consumer<Throwable> errorListener) {
    writeErrorListener = errorListener;
  }

  public void open() {
    open(builder);
  }

  public void open(final UnaryOperator<RaftStorage.Builder> builder) {
    final File directory;
    close();

    try {
      directory = temporaryFolder.newFolder(String.format("atomix-%d", partitionId));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    raftStorage = builder.apply(buildDefaultStorage()).withDirectory(directory).build();
    raftLog = raftStorage.openLog();
    metaStore = raftStorage.openMetaStore();

    storage = spy(new AtomixLogStorage(this, this));
  }

  public void close() {
    Optional.ofNullable(raftLog).ifPresent(RaftLog::close);
    raftLog = null;
    Optional.ofNullable(metaStore).ifPresent(MetaStore::close);
    metaStore = null;
    storage = null;

    if (raftStorage != null) {
      try {
        FileUtil.deleteFolder(raftStorage.directory().toPath());
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }

      raftStorage = null;
    }

    positionListener = null;
    writeErrorListener = null;
  }

  public int getPartitionId() {
    return partitionId;
  }

  private RaftStorage.Builder buildDefaultStorage() {
    return RaftStorage.builder().withFlushExplicitly(true).withJournalIndexDensity(1);
  }
}
