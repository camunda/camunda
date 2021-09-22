/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util;

import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import org.agrona.SystemUtil;
import org.slf4j.Logger;

public final class FileUtil {
  private static final Logger LOG = Loggers.FILE_LOGGER;

  private FileUtil() {}

  /**
   * Ensures updates to the given path are flushed to disk, with the same guarantees as {@link
   * FileChannel#force(boolean)}. This can be used for files only; for directories, call {@link
   * #flushDirectory(Path)}. Note that if you already have a file channel open, then use the {@link
   * FileChannel#force(boolean)} method directly.
   *
   * @param path the path to synchronize
   * @throws IOException can be thrown on opening and on flushing the file
   */
  public static void flush(final Path path) throws IOException {
    try (final var channel = FileChannel.open(path, StandardOpenOption.READ)) {
      channel.force(true);
    }
  }

  /**
   * This method flushes the given directory. Note that on Windows this is a no-op, as Windows does
   * not allow to flush directories except when opening the sys handle with a specific backup flag,
   * which is not possible to do without custom JNI code. However, as the main use case here is for
   * durability to sync the parent directory after creating a new file or renaming it, this is safe
   * to do as Windows (or rather NTFS) does not need this.
   *
   * @param path the path to synchronize
   * @throws IOException can be thrown on opening and on flushing the file
   */
  public static void flushDirectory(final Path path) throws IOException {
    // Windows does not allow flushing a directory except under very specific conditions which are
    // not possible to produce with the standard JDK; it's also not necessary to flush a directory
    // in Windows
    if (SystemUtil.isWindows()) {
      return;
    }

    flush(path);
  }

  /**
   * Moves the given {@code source} file to the {@code target} location, flushing the target's
   * parent directory afterwards to guarantee that the file will be visible and avoid the classic
   * 0-length problem.
   *
   * @param source the file or directory to move
   * @param target the new path the file or directory should have after
   * @param options copy options, e.g. {@link java.nio.file.StandardCopyOption}
   * @throws IOException on either move or flush error
   */
  public static void moveDurably(final Path source, final Path target, final CopyOption... options)
      throws IOException {
    Files.move(source, target, options);
    flushDirectory(target.getParent());
  }

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

  /**
   * A variant of {@link #deleteFolder(Path)}, which ignores missing files. Inspired from {@link
   * Files#deleteIfExists(Path)} which is implemented in much the same way. To be preferred over
   * {@link #deleteFolder(Path)} preceded by a {@link Files#exists(Path, LinkOption...)}, as that is
   * more prone to race conditions.
   *
   * @param folder the directory to delete (if or any of its files exists)
   * @throws IOException on failure to scan the directory and/or delete the file
   */
  public static void deleteFolderIfExists(final Path folder) throws IOException {
    try {
      Files.walkFileTree(folder, new FolderDeleter(Files::deleteIfExists));
    } catch (final NoSuchFileException ignored) { // NOSONAR
      // ignored
    }
  }

  public static void deleteFolder(final Path folder) throws IOException {
    Files.walkFileTree(folder, new FolderDeleter(Files::delete));
  }

  public static void copySnapshot(final Path snapshotDirectory, final Path runtimeDirectory)
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
      Files.copy(dir, newDirectory);
      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
        throws IOException {
      final Path newFile = targetPath.resolve(sourcePath.relativize(file));
      Files.copy(file, newFile);
      return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
      return CONTINUE;
    }
  }

  private static final class FolderDeleter extends SimpleFileVisitor<Path> {
    private final FileDeleter deleter;

    private FolderDeleter(final FileDeleter deleter) {
      this.deleter = Objects.requireNonNull(deleter);
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
        throws IOException {
      deleter.delete(file);
      return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
        throws IOException {
      deleter.delete(dir);
      return CONTINUE;
    }
  }

  @FunctionalInterface
  private interface FileDeleter {
    void delete(Path path) throws IOException;
  }
}
