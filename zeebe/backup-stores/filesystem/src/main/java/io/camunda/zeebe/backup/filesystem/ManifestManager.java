/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.filesystem;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.Manifest.InProgressManifest;
import io.camunda.zeebe.backup.common.Manifest.StatusCode;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManifestManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManifestManager.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);

  /**
   * The path format consists of the following elements:
   *
   * <ul>
   *   <li>{@code manifestsPath}
   *   <li>{@code partitionId}
   *   <li>{@code checkpointId}
   *   <li>{@code nodeId}
   *   <li>{@code "manifest.json"}
   * </ul>
   *
   * The path format is constructed by {@code
   * manifestsPath}/partitionId/checkpointId/nodeId/manifest.json
   */
  private static final String MANIFEST_FILENAME = "manifest.json";

  private final Path manifestsPath;

  ManifestManager(final Path manifestsPath) {
    this.manifestsPath = manifestsPath;
  }

  InProgressManifest createInitialManifest(final Backup backup) {

    final var manifest = Manifest.createInProgress(backup);
    final byte[] serializedManifest;
    final var path = manifestPath(manifest);

    try {
      FileUtil.ensureDirectoryExists(path.getParent());
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Unable to create directories for manifest: " + path.getParent(), e);
    }

    try {
      serializedManifest = MAPPER.writeValueAsBytes(manifest);
      Files.write(path, serializedManifest, StandardOpenOption.CREATE_NEW, StandardOpenOption.SYNC);
      FileUtil.flushDirectory(path.getParent());

      return manifest;
    } catch (final FileAlreadyExistsException e) {
      throw new UnexpectedManifestState("Manifest already exists.");
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to write manifest to " + path, e);
    }
  }

  void completeManifest(final InProgressManifest inProgressManifest) {
    final byte[] serializedManifest;
    final var completed = inProgressManifest.complete();
    try {
      serializedManifest = MAPPER.writeValueAsBytes(completed);

      final Manifest existingManifest = getManifest(inProgressManifest.id());
      if (existingManifest == null) {
        throw new UnexpectedManifestState("Manifest does not exist.");
      } else if (existingManifest.statusCode() != StatusCode.IN_PROGRESS) {
        throw new UnexpectedManifestState(
            "Expected manifest to be in progress but was in %s"
                .formatted(existingManifest.statusCode().name()));
      }
      final var path = manifestPath(inProgressManifest);
      Files.write(path, serializedManifest, StandardOpenOption.CREATE, StandardOpenOption.SYNC);
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to write updated manifest", e);
    }
  }

  void markAsFailed(final BackupIdentifier manifestId, final String failureReason) {
    Manifest manifest = getManifest(manifestId);
    if (manifest == null) {
      manifest = Manifest.createFailed(manifestId);
    }

    final var updatedManifest =
        switch (manifest.statusCode()) {
          case FAILED -> manifest.asFailed();
          case COMPLETED -> manifest.asCompleted().fail(failureReason);
          case IN_PROGRESS -> manifest.asInProgress().fail(failureReason);
        };

    if (manifest != updatedManifest) {
      try {
        final var serializedManifest = MAPPER.writeValueAsBytes(updatedManifest);
        final var path = manifestPath(manifest);
        Files.write(path, serializedManifest, StandardOpenOption.SYNC);
      } catch (final IOException e) {
        throw new UncheckedIOException("Unable to write updated manifest", e);
      }
    }
  }

  void deleteManifest(final BackupIdentifier id) {
    final Manifest manifest = getManifest(id);
    if (manifest == null) {
      return;
    } else if (manifest.statusCode() == StatusCode.IN_PROGRESS) {
      throw new UnexpectedManifestState(
          "Cannot delete Backup with id '%s' while saving is in progress."
              .formatted(id.toString()));
    }

    try {
      final var path = manifestPath(manifest);
      Files.delete(path);
      final var dirLimit = manifestsPath.resolve(String.valueOf(id.partitionId()));
      FilesystemBackupStore.backtrackDeleteEmptyParents(path.getParent(), dirLimit);
    } catch (final NoSuchFileException e) {
      LOGGER.warn("Try to remove unknown manifest with id {}", id);
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to delete manifest", e);
    }
  }

  Manifest getManifest(final BackupIdentifier id) {
    return getManifestWithPath(getManifestPath(id));
  }

  Collection<Manifest> listManifests(final BackupIdentifierWildcard wildcard) {
    try (final Stream<Path> files = Files.walk(manifestsPath)) {
      return files
          .filter(filePath -> filterBlobsByWildcard(wildcard, filePath.toString()))
          .map(this::getManifestWithPath)
          .toList();
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to list manifests from " + manifestsPath, e);
    }
  }

  private Manifest getManifestWithPath(final Path path) {
    if (!Files.exists(path)) {
      return null;
    }

    try {
      return MAPPER.readValue(path.toFile(), Manifest.class);
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to read manifest from path " + path, e);
    }
  }

  @VisibleForTesting
  Path manifestPath(final Manifest manifest) {
    return getManifestPath(manifest.id());
  }

  private Path getManifestPath(final BackupIdentifier id) {
    return getManifestPath(
        String.valueOf(id.partitionId()),
        String.valueOf(id.checkpointId()),
        String.valueOf(id.nodeId()));
  }

  private boolean filterBlobsByWildcard(
      final BackupIdentifierWildcard wildcard, final String path) {
    final var pattern =
        Pattern.compile(
                getManifestPath(
                        wildcard.partitionId().map(Number::toString).orElse("\\d+"),
                        wildcard.checkpointPattern().asRegex(),
                        wildcard.nodeId().map(Number::toString).orElse("\\d+"))
                    .toString())
            .asMatchPredicate();
    return pattern.test(path);
  }

  private Path getManifestPath(
      final String partitionId, final String checkpointId, final String nodeId) {
    return manifestsPath
        .resolve(partitionId)
        .resolve(checkpointId)
        .resolve(nodeId)
        .resolve(MANIFEST_FILENAME);
  }
}
