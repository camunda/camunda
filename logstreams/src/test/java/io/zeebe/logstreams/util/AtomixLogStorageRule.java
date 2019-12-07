/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import static org.mockito.Mockito.spy;

import io.atomix.protocols.raft.partition.impl.RaftNamespaces;
import io.atomix.protocols.raft.storage.RaftStorage;
import io.atomix.protocols.raft.storage.log.RaftLog;
import io.atomix.protocols.raft.storage.log.RaftLogReader;
import io.atomix.protocols.raft.storage.snapshot.SnapshotStore;
import io.atomix.protocols.raft.storage.system.MetaStore;
import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.protocols.raft.zeebe.ZeebeLogAppender;
import io.atomix.storage.StorageLevel;
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.JournalReader.Mode;
import io.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.storage.atomix.AtomixAppenderSupplier;
import io.zeebe.logstreams.storage.atomix.AtomixLogCompactor;
import io.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.zeebe.logstreams.storage.atomix.AtomixReaderFactory;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

public class AtomixLogStorageRule extends ExternalResource
    implements AtomixLogCompactor,
        AtomixReaderFactory,
        AtomixAppenderSupplier,
        ZeebeLogAppender,
        Supplier<LogStorage> {
  private final LoggedEventImpl event = new LoggedEventImpl();
  private final TemporaryFolder temporaryFolder;
  private final int partitionId;
  private final UnaryOperator<RaftStorage.Builder> builder;

  private RaftStorage raftStorage;
  private RaftLog raftLog;
  private SnapshotStore snapshotStore;
  private MetaStore metaStore;

  private AtomixLogStorage storage;
  private LongConsumer positionListener;

  public AtomixLogStorageRule(final TemporaryFolder temporaryFolder) {
    this(temporaryFolder, 0);
  }

  public AtomixLogStorageRule(final TemporaryFolder temporaryFolder, final int partitionId) {
    this(temporaryFolder, partitionId, UnaryOperator.identity());
  }

  public AtomixLogStorageRule(
      final TemporaryFolder temporaryFolder,
      final int partitionId,
      final UnaryOperator<RaftStorage.Builder> builder) {
    this.temporaryFolder = temporaryFolder;
    this.partitionId = partitionId;
    this.builder = builder;
  }

  @Override
  public void before() throws Throwable {
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
    final Indexed<ZeebeEntry> entry =
        raftLog
            .writer()
            .append(
                new ZeebeEntry(
                    0, System.currentTimeMillis(), lowestPosition, highestPosition, data));
    listener.onWrite(entry);
    raftLog.writer().commit(entry.index());

    if (positionListener != null) {
      positionListener.accept(findGreatestPosition(entry));
    }

    listener.onCommit(entry);
  }

  @Override
  public AtomixLogStorage get() {
    return storage;
  }

  @Override
  public Optional<ZeebeLogAppender> getAppender() {
    return Optional.of(this);
  }

  @Override
  public CompletableFuture<Void> compact(final long index) {
    raftLog.compact(index);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public RaftLogReader create(final long index, final Mode mode) {
    return raftLog.openReader(index, mode);
  }

  public void setPositionListener(final LongConsumer positionListener) {
    this.positionListener = positionListener;
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
    snapshotStore = raftStorage.getSnapshotStore();
    metaStore = raftStorage.openMetaStore();

    storage = spy(new AtomixLogStorage(this, this, this));
  }

  public void close() {
    Optional.ofNullable(raftLog).ifPresent(RaftLog::close);
    Optional.ofNullable(snapshotStore).ifPresent(SnapshotStore::close);
  }

  public int getPartitionId() {
    return partitionId;
  }

  public AtomixLogStorage getStorage() {
    return storage;
  }

  public RaftStorage getRaftStorage() {
    return raftStorage;
  }

  public RaftLog getRaftLog() {
    return raftLog;
  }

  public SnapshotStore getSnapshotStore() {
    return snapshotStore;
  }

  public MetaStore getMetaStore() {
    return metaStore;
  }

  private RaftStorage.Builder buildDefaultStorage() {
    return RaftStorage.builder()
        .withFlushOnCommit()
        .withStorageLevel(StorageLevel.DISK)
        .withNamespace(RaftNamespaces.RAFT_STORAGE)
        .withRetainStaleSnapshots();
  }

  private long findGreatestPosition(final Indexed<ZeebeEntry> indexed) {
    final var entry = indexed.entry();
    final var data = new UnsafeBuffer(entry.data());

    var offset = 0;
    do {
      event.wrap(data, offset);
      offset += event.getLength();
    } while (offset < data.capacity());

    return event.getPosition();
  }
}
