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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ManifestManager {

  /**
   * The path format consists of the following elements:
   *
   * <ul>
   *   <li>{@code "manifests"}
   *   <li>{@code partitionId}
   *   <li>{@code checkpointId}
   *   <li>{@code nodeId}
   *   <li>{@code "manifest.json"}
   * </ul>
   *
   * The path format is constructed by partitionId/checkpointId/nodeId/manifest.json
   */
  private static final String MANIFEST_PATH_FORMAT = "%s/manifests/%s/%s/%s/manifest.json";

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);
  private final String basePath;

  ManifestManager(final String basePath) {
    this.basePath = basePath;
  }

  InProgressManifest createInitialManifest(final Backup backup) {

    final var manifest = Manifest.createInProgress(backup);
    final byte[] serializedManifest;
    try {
      final var path = manifestPath(manifest);
      Files.createDirectories(Path.of(path.substring(0, path.length() - 13)));
      System.out.println(Path.of(path).toAbsolutePath());

      serializedManifest = MAPPER.writeValueAsBytes(manifest);
      Files.write(Path.of(path), serializedManifest, StandardOpenOption.CREATE_NEW);

      return manifest;
    } catch (final IOException e) {
      throw new RuntimeException(e);
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

      Files.write(Path.of(path), serializedManifest, StandardOpenOption.CREATE);
    } catch (final IOException e) {
      throw new RuntimeException(e);
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
        final var serializedManifest = MAPPER.writeValueAsBytes(manifest);
        final var path = manifestPath(manifest);
        Files.write(Path.of(path), serializedManifest, StandardOpenOption.CREATE);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void deleteManifest(final BackupIdentifier id) {
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
      Files.delete(Path.of(path));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  Manifest getManifest(final BackupIdentifier id) {
    return getManifestWithPath(
        Path.of(MANIFEST_PATH_FORMAT.formatted(basePath, id.partitionId(), id.checkpointId(),
            id.nodeId())));
  }

  private Manifest getManifestWithPath(final Path path) {
    if (!Files.exists(path)) {
      return null;
    }

    try {
      final var binaryData = Files.readAllBytes(path);
      return MAPPER.readValue(binaryData, Manifest.class);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Collection<Manifest> listManifests(final BackupIdentifierWildcard wildcard) {
    try (final Stream<Path> files = Files.walk(Path.of(basePath + "/manifests/"))) {
      return files
          .filter(filePath -> filterBlobsByWildcard(wildcard, filePath.toString()))
          .map(this::getManifestWithPath)
          .toList();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String manifestPath(final Manifest manifest) {
    return manifestIdPath(manifest.id());
  }

  private String manifestIdPath(final BackupIdentifier backupIdentifier) {
    return MANIFEST_PATH_FORMAT.formatted(basePath,
        backupIdentifier.partitionId(), backupIdentifier.checkpointId(), backupIdentifier.nodeId());
  }

  private boolean filterBlobsByWildcard(
      final BackupIdentifierWildcard wildcard, final String path) {
    final var pattern =
        Pattern.compile(
                MANIFEST_PATH_FORMAT.formatted(
                    basePath,
                    wildcard.partitionId().map(Number::toString).orElse("\\d+"),
                    wildcard.checkpointId().map(Number::toString).orElse("\\d+"),
                    wildcard.nodeId().map(Number::toString).orElse("\\d+")))
            .asMatchPredicate();
    return pattern.test(path);
  }
}
