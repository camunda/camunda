/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.filesystem;

import io.camunda.zeebe.backup.api.BackupIndexFile;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FilesystemIndexManager {
  private static final Logger LOG = LoggerFactory.getLogger(FilesystemIndexManager.class);

  private final Path indexBaseDir;

  FilesystemIndexManager(final Path indexBaseDir) {
    this.indexBaseDir = indexBaseDir;
  }

  FilesystemBackupIndexFile upload(final FilesystemBackupIndexFile indexFile) {
    final Path targetPath = indexPath(indexFile.id());
    try {
      FileUtil.ensureDirectoryExists(targetPath.getParent());
      Files.copy(indexFile.path(), targetPath, StandardCopyOption.REPLACE_EXISTING);
      FileUtil.flush(targetPath);
      FileUtil.flushDirectory(targetPath.getParent());
      LOG.debug("Uploaded index {} to {}", indexFile.id(), targetPath);
      return indexFile;
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to upload index " + indexFile.id(), e);
    }
  }

  BackupIndexFile download(final BackupIndexIdentifier id, final Path targetPath) {
    if (Files.exists(targetPath)) {
      throw new IllegalArgumentException("Index file already exists at " + targetPath);
    }

    final Path sourcePath = indexPath(id);

    if (!Files.exists(sourcePath)) {
      LOG.debug("Index {} not found in filesystem", id);
      try {
        Files.createFile(targetPath);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      return new FilesystemBackupIndexFile(targetPath, id);
    }

    try {
      Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
      LOG.debug("Downloaded index {} to {}", id, targetPath);
      return new FilesystemBackupIndexFile(targetPath, id);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Path indexPath(final BackupIndexIdentifier id) {
    return indexBaseDir.resolve(
        String.format("%s/%s/%s", id.partitionId(), id.nodeId(), "index.bin"));
  }
}
