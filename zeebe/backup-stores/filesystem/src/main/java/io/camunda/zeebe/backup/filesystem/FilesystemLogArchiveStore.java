/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.filesystem;

import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * {@link LogArchiveStore} on a local directory. Objects are written to a temporary file below
 * {@code <base>/.tmp} and atomically moved into place, so readers never see partial objects.
 */
public final class FilesystemLogArchiveStore implements LogArchiveStore {

  private static final String TMP_DIR = ".tmp";

  private final Path basePath;
  private final Path tmpPath;
  private final ExecutorService executor;

  private FilesystemLogArchiveStore(final Path basePath, final ExecutorService executor) {
    this.basePath = basePath.toAbsolutePath().normalize();
    tmpPath = this.basePath.resolve(TMP_DIR);
    this.executor = executor;
  }

  public static FilesystemLogArchiveStore of(final FilesystemBackupConfig config) {
    final var base = config.basePath();
    if (base == null || base.isBlank()) {
      throw new IllegalArgumentException("basePath must be set for the log archive store");
    }
    return new FilesystemLogArchiveStore(
        Path.of(base), Executors.newVirtualThreadPerTaskExecutor());
  }

  @Override
  public CompletableFuture<Void> put(final String key, final byte[] content) {
    return run(
        () -> {
          final var tmp = newTmpFile();
          try {
            Files.write(tmp, content, StandardOpenOption.WRITE);
            moveIntoPlace(tmp, resolve(key));
          } finally {
            Files.deleteIfExists(tmp);
          }
        });
  }

  @Override
  public CompletableFuture<Void> putFile(final String key, final Path file) {
    return run(
        () -> {
          final var tmp = newTmpFile();
          try {
            Files.copy(file, tmp, StandardCopyOption.REPLACE_EXISTING);
            moveIntoPlace(tmp, resolve(key));
          } finally {
            Files.deleteIfExists(tmp);
          }
        });
  }

  @Override
  public CompletableFuture<Optional<byte[]>> getIfExists(final String key) {
    return supply(
        () -> {
          try {
            return Optional.of(Files.readAllBytes(resolve(key)));
          } catch (final NoSuchFileException e) {
            return Optional.empty();
          }
        });
  }

  @Override
  public CompletableFuture<byte[]> getRange(final String key, final long offset, final int length) {
    return supply(
        () -> {
          try (final var channel =
              java.nio.channels.FileChannel.open(resolve(key), StandardOpenOption.READ)) {
            final long available = Math.max(0, channel.size() - offset);
            final var buffer = java.nio.ByteBuffer.allocate((int) Math.min(length, available));
            long position = offset;
            while (buffer.hasRemaining()) {
              final int read = channel.read(buffer, position);
              if (read < 0) {
                break;
              }
              position += read;
            }
            return java.util.Arrays.copyOf(buffer.array(), buffer.position());
          }
        });
  }

  @Override
  public CompletableFuture<Void> getToFile(final String key, final Path target) {
    return run(() -> Files.copy(resolve(key), target, StandardCopyOption.REPLACE_EXISTING));
  }

  @Override
  public CompletableFuture<List<String>> list(final String prefix) {
    return supply(
        () -> {
          // walk only below the prefix's directory; objects are put and deleted concurrently
          final int slash = prefix.lastIndexOf('/');
          final var start =
              basePath.resolve(slash < 0 ? "" : prefix.substring(0, slash)).normalize();
          if (!start.startsWith(basePath) || !Files.isDirectory(start)) {
            return List.of();
          }
          final var keys = new ArrayList<String>();
          Files.walkFileTree(
              start,
              new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(
                    final Path dir, final BasicFileAttributes attrs) {
                  return dir.startsWith(tmpPath)
                      ? FileVisitResult.SKIP_SUBTREE
                      : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                  if (attrs.isRegularFile()) {
                    final var key =
                        basePath
                            .relativize(file)
                            .toString()
                            .replace(file.getFileSystem().getSeparator(), "/");
                    if (key.startsWith(prefix)) {
                      keys.add(key);
                    }
                  }
                  return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path file, final IOException e)
                    throws IOException {
                  if (e instanceof NoSuchFileException) {
                    return FileVisitResult.CONTINUE;
                  }
                  throw e;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException e)
                    throws IOException {
                  if (e != null && !(e instanceof NoSuchFileException)) {
                    throw e;
                  }
                  return FileVisitResult.CONTINUE;
                }
              });
          Collections.sort(keys);
          return keys;
        });
  }

  @Override
  public CompletableFuture<List<String>> listChildPrefixes(final String prefix) {
    if (!prefix.endsWith("/")) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Expected a prefix ending with '/', but got: " + prefix));
    }
    return supply(
        () -> {
          final var directory = basePath.resolve(prefix).normalize();
          if (!directory.startsWith(basePath)
              || directory.startsWith(tmpPath)
              || !Files.isDirectory(directory)) {
            return List.of();
          }
          final var names = new ArrayList<String>();
          try (final Stream<Path> children = Files.list(directory)) {
            children
                .filter(Files::isDirectory)
                .map(child -> child.getFileName().toString())
                .forEach(names::add);
          }
          Collections.sort(names);
          return names;
        });
  }

  @Override
  public CompletableFuture<Void> delete(final String key) {
    return run(() -> Files.deleteIfExists(resolve(key)));
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    executor.shutdown();
    return CompletableFuture.completedFuture(null);
  }

  private Path resolve(final String key) {
    final var resolved = basePath.resolve(key).normalize();
    if (!resolved.startsWith(basePath)
        || resolved.startsWith(tmpPath)
        || resolved.equals(basePath)) {
      throw new IllegalArgumentException("Invalid key: " + key);
    }
    return resolved;
  }

  private Path newTmpFile() throws IOException {
    Files.createDirectories(tmpPath);
    return Files.createTempFile(tmpPath, "put-", ".tmp");
  }

  private static void moveIntoPlace(final Path tmp, final Path target) throws IOException {
    Files.createDirectories(java.util.Objects.requireNonNull(target.getParent()));
    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  private CompletableFuture<Void> run(final IoRunnable action) {
    return supply(
            () -> {
              action.run();
              return Boolean.TRUE;
            })
        .thenApply(ignored -> null);
  }

  private <T> CompletableFuture<T> supply(final IoSupplier<T> action) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return action.get();
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        },
        (Executor) executor);
  }

  @FunctionalInterface
  private interface IoRunnable {
    void run() throws IOException;
  }

  @FunctionalInterface
  private interface IoSupplier<T> {
    T get() throws IOException;
  }
}
