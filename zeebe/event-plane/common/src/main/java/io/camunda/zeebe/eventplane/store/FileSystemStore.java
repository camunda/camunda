/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An {@link ObjectStore} on a filesystem: a key is the path of a file below the root. {@link #put}
 * writes a temporary file next to the object, and renames it, so a reader sees the old content or
 * the new, never part of it. Files that start with {@value #TEMPORARY_PREFIX} are those temporary
 * files, and no listing shows them.
 */
public final class FileSystemStore implements ObjectStore {

  static final String TEMPORARY_PREFIX = ".ep-tmp-";

  private final Path root;
  private final URI rootUri;

  public FileSystemStore(final Path root) {
    this.root = root.toAbsolutePath().normalize();
    final var uri = this.root.toUri().toString();
    rootUri = URI.create(uri.endsWith("/") ? uri : uri + "/");
  }

  @Override
  public URI root() {
    return rootUri;
  }

  @Override
  public void put(final String key, final byte[] content) throws IOException {
    final var target = resolve(key);
    final var directory = Objects.requireNonNull(target.getParent());
    Files.createDirectories(directory);
    final var temporary = directory.resolve(TEMPORARY_PREFIX + UUID.randomUUID());
    try {
      Files.write(temporary, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (final IOException | RuntimeException e) {
      try {
        Files.deleteIfExists(temporary);
      } catch (final IOException suppressed) {
        e.addSuppressed(suppressed);
      }
      throw e;
    }
  }

  @Override
  public Optional<byte[]> get(final String key) throws IOException {
    final var path = resolve(key);
    if (!Files.isRegularFile(path)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readAllBytes(path));
    } catch (final NoSuchFileException e) {
      return Optional.empty();
    }
  }

  @Override
  public byte[] getRange(final String key, final long offset, final int length) throws IOException {
    if (offset < 0 || length < 0) {
      throw new IllegalArgumentException(
          "Expected an offset and a length of 0 or more, but got %d and %d"
              .formatted(offset, length));
    }
    final var path = resolve(key);
    if (!Files.isRegularFile(path)) {
      throw new NoSuchObjectException(key);
    }
    try (final var channel = FileChannel.open(path, StandardOpenOption.READ)) {
      final long size = channel.size();
      if (offset >= size || length == 0) {
        return new byte[0];
      }
      final var buffer = ByteBuffer.allocate((int) Math.min(length, size - offset));
      long position = offset;
      while (buffer.hasRemaining()) {
        final int read = channel.read(buffer, position);
        if (read < 0) {
          break;
        }
        position += read;
      }
      return buffer.hasRemaining()
          ? Arrays.copyOf(buffer.array(), buffer.position())
          : buffer.array();
    } catch (final NoSuchFileException e) {
      throw new NoSuchObjectException(key, e);
    }
  }

  @Override
  public Optional<StoredObject> head(final String key) throws IOException {
    final var path = resolve(key);
    if (!Files.isRegularFile(path)) {
      return Optional.empty();
    }
    try {
      final var attributes = Files.readAttributes(path, BasicFileAttributes.class);
      return Optional.of(storedObject(key, attributes));
    } catch (final NoSuchFileException e) {
      return Optional.empty();
    }
  }

  @Override
  public List<StoredObject> list(
      final String prefix, @Nullable final String startAfter, final int limit) throws IOException {
    ObjectKeys.requireLimit(limit);
    final var objects = new ArrayList<StoredObject>();
    if (limit > 0) {
      collect(ObjectKeys.directoryOf(prefix), prefix, startAfter, limit, objects);
    }
    return objects;
  }

  @Override
  public List<String> listPrefixes(final String prefix) throws IOException {
    ObjectKeys.requireDirectory(prefix);
    final var prefixes = new ArrayList<String>();
    for (final var entry : children(prefix, "")) {
      if (entry.attributes().isDirectory()
          && holdsObject(directory(prefix).resolve(entry.name()))) {
        prefixes.add(prefix + entry.name() + "/");
      }
    }
    return prefixes;
  }

  @Override
  public void delete(final String key) throws IOException {
    final var path = resolve(key);
    if (Files.isRegularFile(path)) {
      Files.deleteIfExists(path);
    }
  }

  @Override
  public String toString() {
    return "FileSystemStore[" + root + "]";
  }

  private Path resolve(final String key) {
    return root.resolve(ObjectKeys.requireKey(key));
  }

  private Path directory(final String directoryKey) {
    return directoryKey.isEmpty() ? root : root.resolve(directoryKey);
  }

  /**
   * Adds the objects below a directory in key order, until there are {@code limit}. A directory is
   * visited only if it may hold a key that starts with the prefix and sorts after {@code
   * startAfter}.
   */
  private void collect(
      final String directoryKey,
      final String prefix,
      @Nullable final String startAfter,
      final int limit,
      final List<StoredObject> objects)
      throws IOException {
    // Only in the directory that the prefix ends in does it say how names start.
    final var namePrefix =
        prefix.length() > directoryKey.length() ? prefix.substring(directoryKey.length()) : "";
    for (final var entry : children(directoryKey, namePrefix)) {
      if (objects.size() >= limit) {
        return;
      }
      if (entry.attributes().isDirectory()) {
        final var key = directoryKey + entry.name() + "/";
        if (mayHoldKeys(key, prefix, startAfter)) {
          collect(key, prefix, startAfter, limit, objects);
        }
      } else if (entry.attributes().isRegularFile()) {
        final var key = directoryKey + entry.name();
        if (startAfter == null || KeyOrder.compare(key, startAfter) > 0) {
          objects.add(storedObject(key, entry.attributes()));
        }
      }
    }
  }

  /** Whether some key below the directory starts with the prefix and sorts after the other key. */
  private static boolean mayHoldKeys(
      final String directoryKey, final String prefix, @Nullable final String startAfter) {
    if (!directoryKey.startsWith(prefix) && !prefix.startsWith(directoryKey)) {
      return false;
    }
    // Every key below the directory starts with its key, so all of them sort before startAfter if
    // the directory's does and startAfter isn't below the directory.
    return startAfter == null
        || startAfter.startsWith(directoryKey)
        || KeyOrder.compare(directoryKey, startAfter) > 0;
  }

  /**
   * The files and directories in a directory whose names start with {@code namePrefix}, in the
   * order of their keys: a directory's key ends in a slash, which puts it among its neighbors
   * differently than a file of the same name.
   */
  private List<Entry> children(final String directoryKey, final String namePrefix)
      throws IOException {
    final var entries = new ArrayList<Entry>();
    try (final DirectoryStream<Path> stream = Files.newDirectoryStream(directory(directoryKey))) {
      for (final var path : stream) {
        final var name = path.getFileName().toString();
        if (name.startsWith(namePrefix) && !name.startsWith(TEMPORARY_PREFIX)) {
          try {
            entries.add(new Entry(name, Files.readAttributes(path, BasicFileAttributes.class)));
          } catch (final NoSuchFileException e) {
            // Deleted since the directory was read.
          }
        }
      }
    } catch (final NoSuchFileException | NotDirectoryException e) {
      return List.of();
    }
    entries.sort(Comparator.comparing(Entry::keyName, KeyOrder.COMPARATOR));
    return entries;
  }

  /** Whether an object is somewhere below the directory. */
  private static boolean holdsObject(final Path directory) throws IOException {
    try (final var paths = Files.walk(directory)) {
      return paths.anyMatch(
          path ->
              !path.getFileName().toString().startsWith(TEMPORARY_PREFIX)
                  && Files.isRegularFile(path));
    } catch (final NoSuchFileException e) {
      return false;
    } catch (final UncheckedIOException e) {
      if (e.getCause() instanceof NoSuchFileException) {
        return false;
      }
      throw e.getCause();
    }
  }

  private static StoredObject storedObject(final String key, final BasicFileAttributes attributes) {
    return new StoredObject(key, attributes.size(), attributes.lastModifiedTime().toInstant());
  }

  private record Entry(String name, BasicFileAttributes attributes) {

    String keyName() {
      return attributes.isDirectory() ? name + "/" : name;
    }
  }
}
