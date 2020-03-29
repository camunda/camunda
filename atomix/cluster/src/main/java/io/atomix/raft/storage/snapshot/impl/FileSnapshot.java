/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.raft.storage.snapshot.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import io.atomix.raft.storage.snapshot.Snapshot;
import io.atomix.storage.buffer.Buffer;
import io.atomix.storage.buffer.FileBuffer;
import io.atomix.utils.AtomixIOException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** File-based snapshot backed by a {@link FileBuffer}. */
final class FileSnapshot extends DefaultSnapshot {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileSnapshot.class);
  private final DefaultSnapshotFile file;

  FileSnapshot(
      final DefaultSnapshotFile file,
      final DefaultSnapshotDescriptor descriptor,
      final DefaultSnapshotStore store) {
    super(descriptor, store);
    this.file = checkNotNull(file, "file cannot be null");
  }

  /** Deletes the temporary file */
  @Override
  public void close() {
    super.close();

    if (file.temporaryFile() != null) {
      deleteFileSilently(file.temporaryFile().toPath());
    }
  }

  /** Deletes the snapshot file. */
  @Override
  public void delete() {
    LOGGER.debug("Deleting {}", this);
    final Path path = file.file().toPath();

    if (Files.exists(path)) {
      deleteFileSilently(path);
    }
  }

  @Override
  public Snapshot complete() {
    checkNotNull(file.temporaryFile(), "no temporary snapshot file to read from");

    final Buffer buffer =
        FileBuffer.allocate(file.temporaryFile(), DefaultSnapshotDescriptor.BYTES);
    try (final DefaultSnapshotDescriptor descriptor = new DefaultSnapshotDescriptor(buffer)) {
      descriptor.lock();
    }

    try {
      Files.move(file.temporaryFile().toPath(), file.file().toPath());
    } catch (final FileAlreadyExistsException e) {
      LOGGER.debug("Snapshot {} was already previously completed", this);
    } catch (final IOException e) {
      throw new AtomixIOException(e);
    }

    file.clearTemporaryFile();
    return super.complete();
  }

  @Override
  public void closeWriter(final SnapshotWriter writer) {
    final int length =
        writer.buffer().position() - (DefaultSnapshotDescriptor.BYTES + Integer.BYTES);
    writer.buffer().writeInt(DefaultSnapshotDescriptor.BYTES, length).flush();
    super.closeWriter(writer);
  }

  @Override
  public Path getPath() {
    return file.file().toPath();
  }

  @Override
  public synchronized SnapshotWriter openWriter() {
    checkWriter();
    checkState(!file.file().exists(), "cannot write to completed snapshot");
    checkNotNull(file.temporaryFile(), "missing temporary snapshot file for writing");
    final Buffer buffer =
        FileBuffer.allocate(file.temporaryFile(), DefaultSnapshotDescriptor.BYTES);
    descriptor.copyTo(buffer);

    final int length = buffer.position(DefaultSnapshotDescriptor.BYTES).readInt();
    return openWriter(new SnapshotWriter(buffer.skip(length).mark(), this), descriptor);
  }

  @Override
  public synchronized SnapshotReader openReader() {
    checkState(file.file().exists(), "missing snapshot file: %s", file.file());
    final Buffer buffer = FileBuffer.allocate(file.file(), DefaultSnapshotDescriptor.BYTES);
    final DefaultSnapshotDescriptor descriptor = new DefaultSnapshotDescriptor(buffer);
    final int length = buffer.position(DefaultSnapshotDescriptor.BYTES).readInt();
    return openReader(
        new SnapshotReader(
            buffer.mark().limit(DefaultSnapshotDescriptor.BYTES + Integer.BYTES + length), this),
        descriptor);
  }

  private void deleteFileSilently(final Path path) {
    try {
      Files.delete(path);
    } catch (final IOException e) {
      LOGGER.debug("Failed to delete snapshot file {}", path, e);
    }
  }
}
