/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.util.FileUtil;
import io.prometheus.client.Gauge;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.IntSupplier;

// Does not need to be thread safe, since only the writer thread accesses it
final class SegmentFilePool {
  private static final Gauge SIZE_METRIC =
      Gauge.build()
          .namespace("atomix")
          .name("segment_file_pool_size")
          .help("The number of files in the segment file pool")
          .labelNames("partition")
          .register();
  private final Deque<SegmentFile> freeSegments;
  private final int maxCount;
  private final IntSupplier liveSegmentCount;
  private final Gauge.Child sizeMetric;

  SegmentFilePool(final String name, final int maxCount, final IntSupplier liveSegmentCount) {
    this.maxCount = maxCount;
    this.liveSegmentCount = liveSegmentCount;
    freeSegments = new ArrayDeque<>(maxCount);
    sizeMetric = SIZE_METRIC.labels(name);
  }

  void cleanup(final Path baseDirectory) {
    try (final var files = Files.newDirectoryStream(baseDirectory, "*.pool.tmp")) {
      for (final var file : files) {
        Files.deleteIfExists(file);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Optional<SegmentFile> acquire() {
    final SegmentFile pooledFile;
    final var liveSegmentCount = this.liveSegmentCount.getAsInt();
    synchronized (freeSegments) {
      final int poolSize = freeSegments.size();

      // make sure we can fill up the pool eventually
      if (liveSegmentCount + poolSize >= maxCount) {
        pooledFile = freeSegments.pollLast();
        sizeMetric.set(poolSize);
      } else {
        pooledFile = null;
      }
    }

    return Optional.ofNullable(pooledFile);
  }

  public boolean free(final SegmentFile file, final SegmentDescriptor descriptor) {
    synchronized (freeSegments) {
      final int poolSize = freeSegments.size();

      if (poolSize >= maxCount) {
        return false;
      }

      freeSegments.offer(markForReuse(file, descriptor));
      sizeMetric.set(poolSize);
    }

    return true;
  }

  // TODO: ran into an issue where we tried to move final path over an existing one, which may
  // indicate some concurrency issue
  private SegmentFile markForReuse(final SegmentFile file, final SegmentDescriptor descriptor) {
    final var originalPath = file.getFileMarkedForDeletion();
    final var finalPath = originalPath.resolveSibling("%s.pool".formatted(file.name()));
    final var temporaryPath =
        originalPath.resolveSibling("%s.tmp".formatted(finalPath.getFileName()));

    try {
      Files.move(
          originalPath,
          temporaryPath,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      invalidateSegment(descriptor, temporaryPath);
      FileUtil.moveDurably(
          temporaryPath,
          finalPath,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      return new SegmentFile(finalPath.toFile());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void invalidateSegment(final SegmentDescriptor descriptor, final Path temporaryPath)
      throws IOException {
    final var buffer = ByteBuffer.allocateDirect(descriptor.length());

    try (final var channel = FileChannel.open(temporaryPath, StandardOpenOption.WRITE)) {
      channel.write(buffer, 0);
      channel.force(true);
    } catch (final IOException e) {
      FileUtil.deleteFolderIfExists(temporaryPath);
      throw e;
    }
  }
}
