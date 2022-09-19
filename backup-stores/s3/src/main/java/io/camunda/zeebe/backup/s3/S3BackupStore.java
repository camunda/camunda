/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupDeletionIncomplete;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupInInvalidStateException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReadException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.ManifestParseException;
import io.camunda.zeebe.backup.s3.manifest.InProgressBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.Manifest;
import io.camunda.zeebe.backup.s3.manifest.NoBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.ValidBackupManifest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * {@link BackupStore} for S3. Stores all backups in a given bucket.
 *
 * <p>All created object keys are prefixed by the {@link BackupIdentifier}, with the following
 * scheme: {@code partitionId/checkpointId/nodeId}
 *
 * <p>Each backup contains:
 *
 * <ol>
 *   <li>A 'manifest' object, containing {@link Manifest} serialized as JSON, for example
 *       <pre>partitionId/checkpointId/nodeId/manifest.json</pre>
 *   <li>Objects for snapshot files, additionally prefixed with 'snapshot', for example
 *       <pre>partitionId/checkpointId/nodeId/snapshots/snapshot-file-1</pre>
 *   <li>Objects for segment files, additionally prefixed with 'segments', for example
 *       <pre>partitionId/checkpointId/nodeId/segments/segment-file-1</pre>
 * </ol>
 */
public final class S3BackupStore implements BackupStore {
  static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new Jdk8Module()).registerModule(new JavaTimeModule());
  static final String SNAPSHOT_PREFIX = "snapshot/";
  static final String SEGMENTS_PREFIX = "segments/";
  static final String MANIFEST_OBJECT_KEY = "manifest.json";
  private static final Logger LOG = LoggerFactory.getLogger(S3BackupStore.class);
  private final S3BackupConfig config;
  private final S3AsyncClient client;

  public S3BackupStore(final S3BackupConfig config) {
    this(config, buildClient(config));
  }

  public S3BackupStore(final S3BackupConfig config, final S3AsyncClient client) {
    this.config = config;
    this.client = client;
  }

  public static String objectPrefix(final BackupIdentifier id) {
    return "%s/%s/%s/".formatted(id.partitionId(), id.checkpointId(), id.nodeId());
  }

  public static void validateConfig(final S3BackupConfig config) {
    if (config.bucketName() == null || config.bucketName().isEmpty()) {
      throw new IllegalArgumentException(
          "Configuration for S3 backup store is incomplete. bucketName must not be empty.");
    }
    if (config.region().isEmpty()) {
      LOG.warn(
          "No region configured for S3 backup store. Region will be determined from environment (see https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/region-selection.html#automatically-determine-the-aws-region-from-the-environment)");
    }
    if (config.endpoint().isEmpty()) {
      LOG.warn(
          "No endpoint configured for S3 backup store. Endpoint will be determined from the region");
    }
    if (config.credentials().isEmpty()) {
      LOG.warn(
          "Access credentials (accessKey, secretKey) not configured for S3 backup store. Credentials will be determined from environment (see https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain)");
    }
    // Create a throw away client to verify if all configurations are available. This will throw an
    // exception, if any of the required configuration is not available.
    buildClient(config).close();
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    LOG.info("Saving {}", backup.id());
    return updateManifestObject(
            backup.id(), Manifest::expectNoBackup, manifest -> manifest.asInProgress(backup))
        .thenComposeAsync(
            status -> {
              final var snapshot = saveSnapshotFiles(backup);
              final var segments = saveSegmentFiles(backup);
              return CompletableFuture.allOf(snapshot, segments);
            })
        .thenComposeAsync(
            ignored ->
                updateManifestObject(
                    backup.id(), Manifest::expectInProgress, InProgressBackupManifest::asCompleted))
        .exceptionallyComposeAsync(
            throwable ->
                updateManifestObject(backup.id(), manifest -> manifest.asFailed(throwable))
                    // Mark the returned future as failed.
                    .thenCompose(status -> CompletableFuture.failedStage(throwable)))
        // Discard status, it's either COMPLETED or the future is completed exceptionally
        .thenApply(ignored -> null);
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    LOG.info("Querying status of {}", id);
    return readManifestObject(id).thenApply(Manifest::toStatus);
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    LOG.info("Deleting {}", id);
    return readManifestObject(id)
        .thenApply(
            manifest -> {
              if (manifest.statusCode() == BackupStatusCode.IN_PROGRESS) {
                throw new BackupInInvalidStateException(
                    "Can't delete in-progress backup %s, must be marked as failed first"
                        .formatted(manifest.id()));
              } else {
                return manifest.id();
              }
            })
        .thenComposeAsync(this::listBackupObjects)
        .thenComposeAsync(this::deleteBackupObjects);
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    LOG.info("Restoring {} to {}", id, targetFolder);
    final var backupPrefix = objectPrefix(id);
    return readManifestObject(id)
        .thenApply(Manifest::expectCompleted)
        .thenComposeAsync(
            manifest ->
                downloadNamedFileSet(
                        backupPrefix + SEGMENTS_PREFIX, manifest.segmentFileNames(), targetFolder)
                    .thenCombineAsync(
                        downloadNamedFileSet(
                            backupPrefix + SNAPSHOT_PREFIX,
                            manifest.snapshotFileNames(),
                            targetFolder),
                        (segments, snapshot) ->
                            new BackupImpl(id, manifest.descriptor(), snapshot, segments)));
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(
      final BackupIdentifier id, final String failureReason) {
    LOG.info("Marking {} as failed", id);
    return updateManifestObject(id, manifest -> manifest.asFailed(failureReason))
        .thenApply(Manifest::statusCode);
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    client.close();
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<NamedFileSet> downloadNamedFileSet(
      final String sourcePrefix, final Set<String> fileNames, final Path targetFolder) {
    LOG.debug(
        "Downloading {} files from prefix {} to {}", fileNames.size(), sourcePrefix, targetFolder);
    final var downloadedFiles = new ConcurrentHashMap<String, Path>();
    final CompletableFuture<?>[] futures =
        fileNames.stream()
            .map(
                fileName -> {
                  final var path = targetFolder.resolve(fileName);
                  return client
                      .getObject(
                          req -> req.bucket(config.bucketName()).key(sourcePrefix + fileName), path)
                      .thenApply(response -> downloadedFiles.put(fileName, path));
                })
            .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(futures)
        .thenApply(ignored -> new NamedFileSetImpl(downloadedFiles));
  }

  private CompletableFuture<List<ObjectIdentifier>> listBackupObjects(final BackupIdentifier id) {
    LOG.debug("Listing objects of {}", id);
    return client
        .listObjectsV2(req -> req.bucket(config.bucketName()).prefix(objectPrefix(id)))
        .thenApplyAsync(
            objects ->
                objects.contents().stream()
                    .map(S3Object::key)
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList());
  }

  private CompletableFuture<Void> deleteBackupObjects(
      final Collection<ObjectIdentifier> objectIdentifiers) {
    LOG.debug("Deleting {} objects", objectIdentifiers.size());
    if (objectIdentifiers.isEmpty()) {
      // Nothing to delete, which we must handle because the delete request would be invalid
      return CompletableFuture.completedFuture(null);
    }
    return client
        .deleteObjects(
            req ->
                req.bucket(config.bucketName())
                    .delete(delete -> delete.objects(objectIdentifiers).quiet(true)))
        .thenApplyAsync(
            response -> {
              if (!response.errors().isEmpty()) {
                throw new BackupDeletionIncomplete(
                    "Not all objects belonging to the backup were deleted successfully: "
                        + response.errors());
              }
              return null;
            });
  }

  private CompletableFuture<Manifest> readManifestObject(final BackupIdentifier id) {
    LOG.debug("Reading manifest object of {}", id);
    return client
        .getObject(
            req -> req.bucket(config.bucketName()).key(objectPrefix(id) + MANIFEST_OBJECT_KEY),
            AsyncResponseTransformer.toBytes())
        .thenApply(
            response -> {
              try {
                return (Manifest)
                    MAPPER.readValue(response.asInputStream(), ValidBackupManifest.class);
              } catch (final JsonParseException e) {
                throw new ManifestParseException("Failed to parse manifest object", e);
              } catch (final IOException e) {
                throw new BackupReadException("Failed to read manifest object", e);
              }
            })
        .exceptionally(
            throwable -> {
              // throwable is a `CompletionException`, `getCause` to handle the underlying exception
              if (throwable.getCause() instanceof NoSuchKeyException) {
                LOG.debug("Found no manifest for backup {}", id);
                return new NoBackupManifest(BackupIdentifierImpl.from(id));
              } else if (throwable.getCause() instanceof S3BackupStoreException e) {
                // Exception was already wrapped, no need to re-wrap
                throw e;
              } else {
                throw new BackupReadException(
                    "Failed to read manifest of %s".formatted(id), throwable);
              }
            });
  }

  <T> CompletableFuture<ValidBackupManifest> updateManifestObject(
      final BackupIdentifier id,
      final Function<Manifest, T> typeExpectation,
      final Function<T, ValidBackupManifest> update) {
    return updateManifestObject(id, manifest -> update.apply(typeExpectation.apply(manifest)));
  }

  CompletableFuture<ValidBackupManifest> updateManifestObject(
      final BackupIdentifier id, final Function<Manifest, ValidBackupManifest> update) {
    return readManifestObject(id).thenApply(update).thenComposeAsync(this::writeManifestObject);
  }

  CompletableFuture<ValidBackupManifest> writeManifestObject(final ValidBackupManifest manifest) {
    LOG.debug("Updating manifest of {} to {}", manifest.id(), manifest);
    final AsyncRequestBody body;
    try {
      body = AsyncRequestBody.fromBytes(MAPPER.writeValueAsBytes(manifest));
    } catch (final JsonProcessingException e) {
      return CompletableFuture.failedFuture(e);
    }

    return client
        .putObject(
            request ->
                request
                    .bucket(config.bucketName())
                    .key(objectPrefix(manifest.id()) + MANIFEST_OBJECT_KEY)
                    .build(),
            body)
        .thenApply(resp -> manifest);
  }

  private CompletableFuture<Void> saveSnapshotFiles(final Backup backup) {
    LOG.debug("Saving snapshot files for {}", backup.id());
    final var prefix = objectPrefix(backup.id()) + SNAPSHOT_PREFIX;

    final var futures =
        backup.snapshot().namedFiles().entrySet().stream()
            .map(
                snapshotFile ->
                    saveNamedFile(prefix, snapshotFile.getKey(), snapshotFile.getValue()))
            .toList();

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[] {}));
  }

  private CompletableFuture<Void> saveSegmentFiles(final Backup backup) {
    LOG.debug("Saving segment files for {}", backup.id());
    final var prefix = objectPrefix(backup.id()) + SEGMENTS_PREFIX;
    final var futures =
        backup.segments().namedFiles().entrySet().stream()
            .map(segmentFile -> saveNamedFile(prefix, segmentFile.getKey(), segmentFile.getValue()))
            .toList();

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[] {}));
  }

  private CompletableFuture<PutObjectResponse> saveNamedFile(
      final String prefix, final String fileName, final Path filePath) {
    LOG.trace("Saving file {}({}) in prefix {}", fileName, filePath, prefix);
    return client.putObject(
        put -> put.bucket(config.bucketName()).key(prefix + fileName),
        AsyncRequestBody.fromFile(filePath));
  }

  public static S3AsyncClient buildClient(final S3BackupConfig config) {
    final var builder = S3AsyncClient.builder();
    config.endpoint().ifPresent(endpoint -> builder.endpointOverride(URI.create(endpoint)));
    config.region().ifPresent(region -> builder.region(Region.of(region)));
    config
        .credentials()
        .ifPresent(
            credentials ->
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                            credentials.accessKey(), credentials.secretKey()))));
    return builder.build();
  }
}
