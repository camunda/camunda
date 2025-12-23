/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies the contents of a data directory.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>Skips any directory subtree containing a {@code runtime} segment.
 *   <li>Skips the configured initialization marker file.
 *   <li>Hard-links snapshot files under {@code snapshots} and {@code bootstrap-snapshots} when
 *       possible; otherwise falls back to copying.
 * </ul>
 */
public final class BrokerDataDirectoryCopier {

  private static final Logger LOG = LoggerFactory.getLogger(BrokerDataDirectoryCopier.class);

  private static final String RUNTIME_DIRECTORY = "runtime";

  public void copy(final Path source, final Path target, final String markerFileName)
      throws IOException {
    Files.walkFileTree(
        source,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
              throws IOException {
            if (isRuntimeDirectory(source, dir)) {
              return FileVisitResult.SKIP_SUBTREE;
            }

            Files.createDirectories(target.resolve(source.relativize(dir)));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
              throws IOException {
            final var relative = source.relativize(file);
            if (relative.getFileName().toString().equals(markerFileName)) {
              return FileVisitResult.CONTINUE;
            }

            final var targetFile = target.resolve(relative);
            Files.createDirectories(targetFile.getParent());

            if (isSnapshotFile(relative)) {
              hardLinkOrCopy(file, targetFile);
            } else {
              Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
            }

            FileUtil.flushDirectory(targetFile.getParent());
            return FileVisitResult.CONTINUE;
          }
        });

    FileUtil.flushDirectory(target);
  }

  private void hardLinkOrCopy(final Path sourceFile, final Path targetFile) throws IOException {
    try {
      Files.createLink(targetFile, sourceFile);
    } catch (final IOException e) {
      LOG.warn(
          "Failed to hard-link snapshot file {} to {}; falling back to copy",
          sourceFile,
          targetFile,
          e);
      Files.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
    }
  }

  private boolean isRuntimeDirectory(final Path root, final Path path) {
    final var relative = root.relativize(path);
    for (final var part : relative) {
      if (part.toString().equals(RUNTIME_DIRECTORY)) {
        return true;
      }
    }
    return false;
  }

  private boolean isSnapshotFile(final Path relativePath) {
    for (final var part : relativePath) {
      final var name = part.toString();
      if (name.equals(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY)
          || name.equals(FileBasedSnapshotStoreImpl.SNAPSHOTS_BOOTSTRAP_DIRECTORY)) {
        return true;
      }
    }
    return false;
  }
}
