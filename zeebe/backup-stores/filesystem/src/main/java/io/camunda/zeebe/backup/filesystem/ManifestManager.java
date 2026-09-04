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
import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.ListOptions;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.CheckpointIds;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.Manifest.InProgressManifest;
import io.camunda.zeebe.backup.common.Manifest.StatusCode;
import io.camunda.zeebe.backup.common.PagedReads;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
<<<<<<< HEAD
import java.util.Collection;
=======
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
>>>>>>> 57406a47 (feat: page backup store listings by checkpoint id)
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
      FileUtil.flushDirectory(path.getParent());
    } catch (final NoSuchFileException e) {
      LOGGER.warn("Try to remove unknown manifest with id {}", id);
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to delete manifest", e);
    }
  }

  Manifest getManifest(final BackupIdentifier id) {
    return getManifestWithPath(getManifestPath(id));
  }

<<<<<<< HEAD
  Collection<Manifest> listManifests(final BackupIdentifierWildcard wildcard) {
    try (final Stream<Path> files = Files.walk(manifestsPath)) {
      return files
          .filter(filePath -> filterBlobsByWildcard(wildcard, filePath.toString()))
          .map(this::getManifestWithPath)
          .toList();
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to list manifests from " + manifestsPath, e);
    }
=======
  /** Lists the page of manifests selected by the options, reading only the selected files. */
  List<Manifest> listManifests(final BackupIdentifierWildcard wildcard, final ListOptions options) {
    final var collector = new ManifestCollector(wildcard);
    try {
      Files.walkFileTree(manifestsPath, collector);
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to list manifests from " + manifestsPath, e);
    }
    return PagedReads.readPage(
        collector.manifestFiles(),
        ManifestFile::id,
        options,
        manifestFile -> Optional.ofNullable(getManifestWithPath(manifestFile.path())));
  }

  /**
   * Parses the identifier from a manifest path: partitionId/checkpointId/memberId/manifest.json.
   * Empty if the checkpoint id segment is a digit run too long to fit a {@code long} — a foreign or
   * corrupted file rather than one this store wrote, skipped instead of failing the whole walk.
   */
  private Optional<BackupIdentifier> parseIdentifier(final Path manifestFile) {
    final var relativePath = manifestsPath.relativize(manifestFile);
    final var checkpointId = CheckpointIds.tryParse(relativePath.getName(1).toString());
    if (checkpointId.isEmpty()) {
      LOGGER.warn("Tried interpreting file {} as a backup manifest but failed", manifestFile);
      return Optional.empty();
    }
    final var memberId = BrokerMemberId.from(relativePath.getName(2).toString());
    return Optional.of(
        new BackupIdentifierImpl(
            memberId.nodeIdx(),
            memberId.zone(),
            Integer.parseInt(relativePath.getName(0).toString()),
            checkpointId.getAsLong()));
>>>>>>> 57406a47 (feat: page backup store listings by checkpoint id)
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
<<<<<<< HEAD
=======

  /** A manifest file and the identifier encoded in its path, collected without reading the file. */
  record ManifestFile(BackupIdentifier id, Path path) {}

  /**
   * Collects the path of every manifest matching the wildcard, continuing past entries that a
   * concurrent deletion removes while the traversal is running: an entry gone mid-walk is the same
   * listing the caller would have seen after the delete, so it is skipped rather than failing the
   * caller. The traversal keeps its position and everything collected so far — nothing is
   * re-walked.
   */
  @VisibleForTesting
  final class ManifestCollector extends SimpleFileVisitor<Path> {

    private final BackupIdentifierWildcard wildcard;
    private final List<ManifestFile> manifestFiles = new ArrayList<>();

    @VisibleForTesting
    ManifestCollector(final BackupIdentifierWildcard wildcard) {
      this.wildcard = wildcard;
    }

    @Override
    public @NonNull FileVisitResult visitFile(
        final Path file, final @NonNull BasicFileAttributes attributes) {
      if (filterBlobsByWildcard(wildcard, file.toString())) {
        parseIdentifier(file)
            .filter(wildcard::matches)
            .ifPresent(id -> manifestFiles.add(new ManifestFile(id, file)));
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public @NonNull FileVisitResult visitFileFailed(
        final @NonNull Path file, final @NonNull IOException failure) throws IOException {
      return continueIfDeletedConcurrently(file, failure);
    }

    @Override
    public @NonNull FileVisitResult postVisitDirectory(
        final @NonNull Path directory, final @Nullable IOException failure) throws IOException {
      return failure == null
          ? FileVisitResult.CONTINUE
          : continueIfDeletedConcurrently(directory, failure);
    }

    private FileVisitResult continueIfDeletedConcurrently(
        final Path path, final IOException failure) throws IOException {
      if (failure instanceof NoSuchFileException && !path.equals(manifestsPath)) {
        return FileVisitResult.CONTINUE;
      }
      throw failure;
    }

    @VisibleForTesting
    List<ManifestFile> manifestFiles() {
      return manifestFiles;
    }
  }
>>>>>>> 57406a47 (feat: page backup store listings by checkpoint id)
}
