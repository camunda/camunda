/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.filesystem;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.FileSet;
import io.camunda.zeebe.backup.common.FileSet.NamedFile;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FileSetManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileSetManager.class);

  private final Path contentsPath;

  FileSetManager(final Path contentsPath) {
    this.contentsPath = contentsPath;
  }

  void save(final BackupIdentifier id, final String fileSetName, final NamedFileSet fileSet) {
    final var fileSetPath = fileSetPath(id, fileSetName);

    try {
      FileUtil.ensureDirectoryExists(fileSetPath);
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to create backup directory", e);
    }

    for (final var namedFile : fileSet.namedFiles().entrySet()) {
      final var sourceFileName = namedFile.getKey();
      final var sourceFilePath = namedFile.getValue();

      final var targetFilePath = fileSetPath.resolve(sourceFileName);
      try {
        Files.copy(sourceFilePath, targetFilePath);
        FileUtil.flush(targetFilePath);
      } catch (final IOException e) {
        throw new UncheckedIOException("Unable to copy file " + sourceFilePath, e);
      }
    }

    try {
      FileUtil.flushDirectory(fileSetPath);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Unable to flush directory "
              + fileSetPath
              + " data might not be consistent on the filesystem. Backup should be restarted.",
          e);
    }
  }

  public void delete(final BackupIdentifier id, final String fileSetName) {
    final var fileSetPath = fileSetPath(id, fileSetName);
    try {
      FileUtil.deleteFolder(fileSetPath);
      FilesystemBackupStore.backtrackDeleteEmptyParents(
          fileSetPath.getParent(), String.valueOf(id.partitionId()));
    } catch (final NoSuchFileException e) {
      LOGGER.warn("Try to remove unknown fileset {} in backup {}", fileSetName, id);
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to delete directory " + fileSetPath, e);
    }
  }

  public NamedFileSet restore(
      final BackupIdentifier id,
      final String fileSetName,
      final FileSet fileSet,
      final Path targetFolder) {

    final var pathByName =
        fileSet.files().stream()
            .collect(Collectors.toMap(NamedFile::name, f -> targetFolder.resolve(f.name())));

    final Path fileSetPath = fileSetPath(id, fileSetName);
    for (final var entry : pathByName.entrySet()) {
      final var fileName = entry.getKey();
      final var filePath = entry.getValue();
      final var backupFilePath = fileSetPath.resolve(fileName);

      try {
        Files.copy(backupFilePath, filePath, StandardCopyOption.REPLACE_EXISTING);
        FileUtil.flush(filePath);
      } catch (final IOException e) {
        throw new UncheckedIOException("Unable to restore file " + fileName, e);
      }
    }
    try {
      FileUtil.flushDirectory(targetFolder);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Unable to flush directory "
              + targetFolder
              + ", the restored backup "
              + id
              + " may be incomplete and inconsistent!",
          e);
    }

    return new NamedFileSetImpl(pathByName);
  }

  private Path fileSetPath(final BackupIdentifier id, final String fileSetName) {
    return contentsPath
        .resolve(String.valueOf(id.partitionId()))
        .resolve(String.valueOf(id.checkpointId()))
        .resolve(String.valueOf(id.nodeId()))
        .resolve(fileSetName);
  }
}
