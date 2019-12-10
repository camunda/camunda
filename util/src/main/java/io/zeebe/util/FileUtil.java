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
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.agrona.LangUtil;
import org.slf4j.Logger;

public class FileUtil {

  public static final Logger LOG = Loggers.FILE_LOGGER;

  public static void deleteFolder(String path) throws IOException {
    final Path directory = Paths.get(path);

    deleteFolder(directory);
  }

  public static void deleteFolder(final Path directory) throws IOException {
    Files.walkFileTree(
        directory,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return CONTINUE;
          }
        });
  }

  @SuppressWarnings("resource")
  public static FileChannel openChannel(String filename, boolean create) {
    FileChannel fileChannel = null;
    try {
      final File file = new File(filename);
      if (!file.exists()) {
        if (create) {
          file.getParentFile().mkdirs();
          file.createNewFile();
        } else {
          return null;
        }
      }

      final RandomAccessFile raf = new RandomAccessFile(file, "rw");
      fileChannel = raf.getChannel();
    } catch (Exception e) {
      LangUtil.rethrowUnchecked(e);
    }

    return fileChannel;
  }

  /**
   * Overwrites file at dest with src. Initially tries to simply move file, and only replaces the
   * existing file if it failed to do an atomic move.
   *
   * @param src file to move
   * @param dest file to overwrite (if existing)
   * @throws IOException see {@link Files#move(Path, Path, CopyOption...)}
   */
  public static void replace(Path src, Path dest) throws IOException {
    try {
      Files.move(src, dest, ATOMIC_MOVE);
    } catch (final Exception e) {
      // failed with atomic move, lets try again with normal replace move
      Files.move(src, dest, REPLACE_EXISTING);
    }
  }

  public static void deleteFile(File file) {
    if (file.exists() && !file.delete()) {
      LOG.warn("Failed to delete file '{}'", file);
    }
  }

  public static void copySnapshot(Path runtimeDirectory, Path snapshotDirectory) throws Exception {
    Files.walkFileTree(snapshotDirectory, new SnapshotCopier(snapshotDirectory, runtimeDirectory));
  }

  public static final class SnapshotCopier extends SimpleFileVisitor<Path> {

    private final Path targetPath;
    private final Path sourcePath;

    SnapshotCopier(Path sourcePath, Path targetPath) {
      this.sourcePath = sourcePath;
      this.targetPath = targetPath;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      final Path newDirectory = targetPath.resolve(sourcePath.relativize(dir));
      try {
        Files.copy(dir, newDirectory);
      } catch (FileAlreadyExistsException ioException) {
        LOG.error("Problem on copying snapshot to runtime.", ioException);
        return SKIP_SUBTREE; // skip processing
      }

      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      final Path newFile = targetPath.resolve(sourcePath.relativize(file));

      try {
        Files.copy(file, newFile);
      } catch (IOException ioException) {
        LOG.error("Problem on copying {} to {}.", file, newFile, ioException);
      }

      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      LOG.error("Problem on copying snapshot to runtime.", exc);
      return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
      return CONTINUE;
    }
  }
}
