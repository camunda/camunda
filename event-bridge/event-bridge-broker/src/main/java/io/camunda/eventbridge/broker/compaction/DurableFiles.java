/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Crash-safe file primitives shared by the clean-segment writer and the manifest store: write to a
 * temp file, fsync its bytes, fsync the containing directory, then atomically rename into place.
 *
 * <p>This is the mechanism that makes the cleaner's "everything before commit is reconstructible
 * garbage" discipline hold on real hardware: a reader (or a restart) never observes a half-written
 * file under its final name, because the final name only ever appears via an atomic rename of a
 * fully-fsynced temp file, and the directory entry itself is fsynced so the rename survives a
 * crash.
 *
 * <p>Threading: stateless static helpers; safe to call from the cleaner actor.
 */
final class DurableFiles {

  private DurableFiles() {}

  /**
   * Atomically writes {@code bytes} to {@code target} durably: writes and fsyncs a sibling temp
   * file, atomically renames it onto {@code target}, then fsyncs the directory so the rename is
   * durable.
   *
   * @param target the final path
   * @param tmp the temp path to stage through (must be in the same directory as {@code target})
   * @param bytes the file contents
   * @throws IOException if any step fails
   */
  static void writeAtomically(final Path target, final Path tmp, final byte[] bytes)
      throws IOException {
    try (final FileChannel channel =
        FileChannel.open(
            tmp,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
      final ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
    }
    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    fsyncDir(target.getParent());
  }

  /**
   * Fsyncs a directory so that a create/rename/delete of one of its entries is durable. Some
   * platforms do not permit opening a directory for read; on those this is a best-effort no-op.
   *
   * @param dir the directory to fsync
   */
  static void fsyncDir(final Path dir) {
    if (dir == null) {
      return;
    }
    try (final FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
      channel.force(true);
    } catch (final IOException e) {
      // Directory fsync is unsupported on some filesystems/platforms (notably Windows); the
      // atomic rename above is still the ordering guarantee we rely on for correctness.
    }
  }
}
