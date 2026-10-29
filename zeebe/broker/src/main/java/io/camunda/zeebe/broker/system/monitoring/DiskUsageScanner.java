/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Measures the disk space used by the regular files under a set of directories by walking them.
 * Files that are deleted while the walk runs, such as compacted segments or replaced snapshots, are
 * skipped.
 */
@NullMarked
public final class DiskUsageScanner implements LongSupplier {

  private final List<Path> roots;

  public DiskUsageScanner(final List<Path> roots) {
    this.roots = List.copyOf(roots);
  }

  @Override
  public long getAsLong() {
    final var visitor = new SizeVisitor();
    for (final var root : roots) {
      if (!Files.exists(root)) {
        continue;
      }
      try {
        Files.walkFileTree(root, visitor);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return visitor.bytes;
  }

  public List<Path> roots() {
    return roots;
  }

  private static final class SizeVisitor extends SimpleFileVisitor<Path> {
    // snapshots hard-link the SST files of the runtime state, so each file is counted once
    private final Set<Object> visitedFileKeys = new HashSet<>();
    private long bytes;

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) {
      final @Nullable Object fileKey = attributes.fileKey();
      if (attributes.isRegularFile() && (fileKey == null || visitedFileKeys.add(fileKey))) {
        bytes += attributes.size();
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(final Path file, final IOException exc)
        throws IOException {
      if (exc instanceof NoSuchFileException) {
        return FileVisitResult.CONTINUE;
      }
      throw exc;
    }

    @Override
    public FileVisitResult postVisitDirectory(final Path dir, final @Nullable IOException exc)
        throws IOException {
      if (exc == null || exc instanceof NoSuchFileException) {
        return FileVisitResult.CONTINUE;
      }
      throw exc;
    }
  }
}
