/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.slf4j.Logger;

public final class FileUtil {

  public static final Logger LOG = Loggers.FILE_LOGGER;

  private FileUtil() {}

  public static void deleteFolder(final String path) throws IOException {
    final Path directory = Paths.get(path);

    deleteFolder(directory);
  }

  public static void ensureDirectoryExists(final Path directory) throws IOException {
    if (Files.exists(directory)) {
      if (!Files.isDirectory(directory)) {
        throw new NotDirectoryException(directory.toString());
      }
    } else {
      Files.createDirectories(directory);
    }
  }

  public static void deleteFolder(final Path directory) throws IOException {
    Files.walkFileTree(
        directory,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
              throws IOException {
            Files.delete(dir);
            return CONTINUE;
          }
        });
  }

  public static void copySnapshot(final Path runtimeDirectory, final Path snapshotDirectory)
      throws Exception {
    Files.walkFileTree(snapshotDirectory, new SnapshotCopier(snapshotDirectory, runtimeDirectory));
  }

  public static final class SnapshotCopier extends SimpleFileVisitor<Path> {

    private final Path targetPath;
    private final Path sourcePath;

    SnapshotCopier(final Path sourcePath, final Path targetPath) {
      this.sourcePath = sourcePath;
      this.targetPath = targetPath;
    }

    @Override
    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
        throws IOException {
      final Path newDirectory = targetPath.resolve(sourcePath.relativize(dir));
      try {
        Files.copy(dir, newDirectory);
      } catch (final FileAlreadyExistsException ioException) {
        LOG.error("Problem on copying snapshot to runtime.", ioException);
        return SKIP_SUBTREE; // skip processing
      }

      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
      final Path newFile = targetPath.resolve(sourcePath.relativize(file));

      try {
        Files.copy(file, newFile);
      } catch (final IOException ioException) {
        LOG.error("Problem on copying {} to {}.", file, newFile, ioException);
      }

      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
      LOG.error("Problem on copying snapshot to runtime.", exc);
      return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
      return CONTINUE;
    }
  }
}
