/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.testing;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.ChecksumProvider;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedReceivedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.CRC32C;

public class TestFileBasedSnapshotStore implements ReceivableSnapshotStore {

  private final FileBasedSnapshotStoreImpl snapshotStore;

  public TestFileBasedSnapshotStore(
      final int nodeId, final Path root, final ConcurrencyControl concurrencyControl) {
    snapshotStore =
        new FileBasedSnapshotStoreImpl(
            nodeId, 1, root, new TestChecksumProvider(), concurrencyControl);
    snapshotStore.start();
  }

  @Override
  public boolean hasSnapshotId(final String id) {
    return snapshotStore.hasSnapshotId(id);
  }

  @Override
  public Optional<PersistedSnapshot> getLatestSnapshot() {
    return snapshotStore.getLatestSnapshot();
  }

  @Override
  public ActorFuture<Set<PersistedSnapshot>> getAvailableSnapshots() {
    return snapshotStore.getAvailableSnapshots();
  }

  @Override
  public ActorFuture<Long> getCompactionBound() {
    return snapshotStore.getCompactionBound();
  }

  @Override
  public ActorFuture<Void> purgePendingSnapshots() {
    return snapshotStore.purgePendingSnapshots();
  }

  @Override
  public ActorFuture<Boolean> addSnapshotListener(final PersistedSnapshotListener listener) {
    return snapshotStore.addSnapshotListener(listener);
  }

  @Override
  public ActorFuture<Boolean> removeSnapshotListener(final PersistedSnapshotListener listener) {
    return snapshotStore.removeSnapshotListener(listener);
  }

  @Override
  public long getCurrentSnapshotIndex() {
    return snapshotStore.getCurrentSnapshotIndex();
  }

  @Override
  public ActorFuture<Void> delete() {
    return snapshotStore.delete();
  }

  @Override
  public Path getPath() {
    return snapshotStore.getPath();
  }

  @Override
  public ActorFuture<FileBasedReceivedSnapshot> newReceivedSnapshot(final String snapshotId) {
    return snapshotStore.newReceivedSnapshot(snapshotId);
  }

  @Override
  public void close() {
    snapshotStore.close();
  }

  public void newSnapshot(final long index, final long term, final int size, final Random random) {
    final var chunks =
        IntStream.range(0, size)
            .boxed()
            .map(i -> "chunk-" + i)
            .collect(Collectors.toMap(k -> k, v -> String.valueOf(random.nextLong())));
    final var transientSnapshot =
        snapshotStore.newTransientSnapshot(index, term, index, index).get();
    transientSnapshot.take(p -> writeSnapshot(p, chunks)).join();
    transientSnapshot.persist().join();
  }

  private boolean writeSnapshot(final Path path, final Map<String, String> chunks) {
    try {
      FileUtil.ensureDirectoryExists(path);

      for (final var entry : chunks.entrySet()) {
        final var fileName = path.resolve(entry.getKey());
        final var fileContent = entry.getValue().getBytes(StandardCharsets.UTF_8);
        Files.write(fileName, fileContent, CREATE_NEW, StandardOpenOption.WRITE);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }

  private static final class TestChecksumProvider implements ChecksumProvider {

    @Override
    public Map<String, Long> getSnapshotChecksums(final Path snapshotPath) {
      final HashMap<String, Long> checksums = new HashMap<>();
      try (final var files =
          Files.newDirectoryStream(
              snapshotPath, p -> p.getFileName().toString().startsWith("chunk"))) {
        files.forEach(
            file -> {
              try {
                final var fileContentChecksum = new CRC32C();
                fileContentChecksum.update(Files.readAllBytes(file));
                checksums.put(file.getFileName().toString(), fileContentChecksum.getValue());
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            });
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }

      return checksums;
    }
  }
}
