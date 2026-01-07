/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupRangeMarker;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupDeletionIncomplete;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupInInvalidStateException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReadException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.ManifestParseException;
import io.camunda.zeebe.backup.s3.manifest.FileSet;
import io.camunda.zeebe.backup.s3.manifest.Manifest;
import io.camunda.zeebe.backup.s3.manifest.NoBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.ValidBackupManifest;
import io.camunda.zeebe.backup.s3.util.AsyncAggregatingSubscriber;
import io.camunda.zeebe.util.SemanticVersion;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.LegacyMd5Plugin;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * {@link BackupStore} for S3. Stores all backups in a given bucket.
 *
 * <p>All created object keys are prefixed by the {@link BackupIdentifier}, with the following
 * scheme: {@code basePath/partitionId/checkpointId/nodeId}.
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
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS);
  static final String SNAPSHOT_PREFIX = "snapshot/";
  static final String SEGMENTS_PREFIX = "segments/";
  static final String MANIFEST_OBJECT_KEY = "manifest.json";
  private static final Logger LOG = LoggerFactory.getLogger(S3BackupStore.class);
  private static final int SCAN_PARALLELISM = 16;
  private final Set<Pattern> backupIdentifierPatterns;
  private final S3BackupConfig config;
  private final S3AsyncClient client;
  private final FileSetManager fileSetManager;

  S3BackupStore(final S3BackupConfig config) {
    this(config, buildClient(config));
  }

  S3BackupStore(final S3BackupConfig config, final S3AsyncClient client) {
    this.config = config;
    this.client = client;
    fileSetManager = new FileSetManager(client, config);
    final var basePath = config.basePath();
    final var basePrefix = basePath.map(base -> base + "/").map(Pattern::quote).orElse("");
    final var identifierSuffix = "(?<partitionId>\\d+)/(?<checkpointId>\\d+)/(?<nodeId>\\d+).*";
    backupIdentifierPatterns =
        Set.of(
            Pattern.compile("^" + basePrefix + identifierSuffix),
            Pattern.compile("^" + basePrefix + "manifests/" + identifierSuffix));
  }

  public static BackupStore of(final S3BackupConfig config) {
    return new S3BackupStore(config);
  }

  private Optional<BackupIdentifier> tryParseKeyAsId(final String key) {
    for (final var pattern : backupIdentifierPatterns) {
      final var matcher = pattern.matcher(key);
      if (matcher.matches()) {
        try {
          final var nodeId = Integer.parseInt(matcher.group("nodeId"));
          final var partitionId = Integer.parseInt(matcher.group("partitionId"));
          final var checkpointId = Long.parseLong(matcher.group("checkpointId"));
          return Optional.of(new BackupIdentifierImpl(nodeId, partitionId, checkpointId));
        } catch (final NumberFormatException e) {
          LOG.warn("Tried interpreting key {} as a BackupIdentifier but failed", key, e);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Tries to build the longest possible prefix based on the given wildcard. If the first component
   * of prefix is not present in the wildcard, the prefix will be empty. If the second component of
   * the prefix is empty, the prefix will only contain the first prefix component and so forth.
   *
   * <p>Using the resulting prefix to list objects does not guarantee that returned objects actually
   * match the wildcard, use {@link S3BackupStore#tryParseKeyAsId(String objectKey)} and {@link
   * BackupIdentifierWildcard#matches(BackupIdentifier id)} to ensure that the listed object
   * matches.
   */
  private String wildcardPrefix(final BackupIdentifierWildcard wildcard) {
    return config.basePath().map(base -> base + "/").orElse("")
        + BackupIdentifierWildcard.asPrefix(wildcard);
  }

  private String wildcardPrefixV2(final BackupIdentifierWildcard wildcard) {
    return config.basePath().map(base -> base + "/").orElse("")
        + Directory.MANIFESTS.name
        + "/"
        + BackupIdentifierWildcard.asPrefix(wildcard);
  }

  /**
   * @deprecated use {@link S3BackupStore#objectPrefixV2(BackupIdentifier, Directory)} and {@link
   *     S3BackupStore#objectPrefixV2(BackupIdentifier, Directory)} instead. Maintained only for
   *     querying older backups.
   */
  @Deprecated(since = "8.9")
  public String objectPrefix(final BackupIdentifier id) {
    final var base = config.basePath();
    return base.map(
            s -> "%s/%s/%s/%s/".formatted(s, id.partitionId(), id.checkpointId(), id.nodeId()))
        .orElseGet(() -> "%s/%s/%s/".formatted(id.partitionId(), id.checkpointId(), id.nodeId()));
  }

  public String objectPrefixV2(final BackupIdentifier id, final Directory directory) {
    final var base = config.basePath();
    return base.map(
            s ->
                "%s/%s/%s/%s/%s/"
                    .formatted(s, directory.name, id.partitionId(), id.checkpointId(), id.nodeId()))
        .orElseGet(
            () ->
                "%s/%s/%s/%s/"
                    .formatted(directory.name, id.partitionId(), id.checkpointId(), id.nodeId()));
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
    return updateManifestObject(
            backup.id(), Manifest::expectNoBackup, manifest -> manifest.asInProgress(backup))
        .thenComposeAsync(
            status -> {
              final var snapshot = saveSnapshotFiles(backup);
              final var segments = saveSegmentFiles(backup);

              return CompletableFuture.allOf(snapshot, segments)
                  .thenComposeAsync(
                      ignored ->
                          updateManifestObject(
                              backup.id(),
                              Manifest::expectInProgress,
                              inProgress ->
                                  inProgress.asCompleted(snapshot.join(), segments.join())))
                  .exceptionallyComposeAsync(
                      throwable ->
                          updateManifestObject(
                                  backup.id(), manifest -> manifest.asFailed(throwable))
                              // Mark the returned future as failed.
                              .thenCompose(ignore -> CompletableFuture.failedStage(throwable)));
            })
        // Discard status, it's either COMPLETED or the future is completed exceptionally
        .thenApply(ignored -> null);
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    return readManifestObject(id).thenApply(Manifest::toStatus);
  }

  /**
   * @implNote Even if S3 is unavailable, the returned future may complete successfully.
   */
  @Override
  public CompletableFuture<Collection<BackupStatus>> list(final BackupIdentifierWildcard wildcard) {
    return readManifestObjects(wildcard)
        .thenApplyAsync(manifests -> manifests.stream().map(Manifest::toStatus).toList());
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    return readManifestObject(id)
        .thenApply(
            manifest -> {
              if (manifest.statusCode() == BackupStatusCode.IN_PROGRESS) {
                throw new BackupInInvalidStateException(
                    "Can't delete in-progress backup %s, must be marked as failed first"
                        .formatted(manifest.id()));
              } else {
                return manifest;
              }
            })
        .thenComposeAsync(this::listBackupObjects)
        .thenCombine(
            readManifestObject(id).thenComposeAsync(this::listBackupManifestObjects),
            (contentsObjects, manifestObjects) -> {
              final var allObjects = new ArrayList<>(contentsObjects);
              allObjects.addAll(manifestObjects);
              return allObjects;
            })
        .thenComposeAsync(this::deleteBackupObjects);
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    return readManifestObject(id)
        .thenApply(Manifest::expectCompleted)
        .thenComposeAsync(
            manifest -> {
              final var backupContentsPrefix =
                  derivePath(manifest.descriptor(), manifest.id(), Directory.CONTENTS);

              return fileSetManager
                  .restore(
                      backupContentsPrefix + SEGMENTS_PREFIX, manifest.segmentFiles(), targetFolder)
                  .thenCombineAsync(
                      fileSetManager.restore(
                          backupContentsPrefix + SNAPSHOT_PREFIX,
                          manifest.snapshotFiles(),
                          targetFolder),
                      (segments, snapshot) ->
                          new BackupImpl(id, manifest.descriptor(), snapshot, segments));
            });
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(
      final BackupIdentifier id, final String failureReason) {
    return updateManifestObject(id, manifest -> manifest.asFailed(failureReason))
        .thenApply(Manifest::statusCode);
  }

  @Override
  public CompletableFuture<Collection<BackupRangeMarker>> rangeMarkers(final int partitionId) {
    final var prefix = rangeMarkersPrefix(partitionId);
    return client
        .listObjectsV2(req -> req.bucket(config.bucketName()).prefix(prefix))
        .thenApply(
            response ->
                response.contents().stream()
                    .map(S3Object::key)
                    .map(key -> key.substring(prefix.length()))
                    .map(BackupRangeMarker::fromName)
                    .filter(marker -> marker != null)
                    .toList());
  }

  @Override
  public CompletableFuture<Void> storeRangeMarker(
      final int partitionId, final BackupRangeMarker marker) {
    final var key = rangeMarkersPrefix(partitionId) + BackupRangeMarker.toName(marker);
    return client
        .putObject(req -> req.bucket(config.bucketName()).key(key), AsyncRequestBody.empty())
        .thenApply(resp -> null);
  }

  @Override
  public CompletableFuture<Void> deleteRangeMarker(
      final int partitionId, final BackupRangeMarker marker) {
    final var key = rangeMarkersPrefix(partitionId) + BackupRangeMarker.toName(marker);
    return client
        .deleteObject(req -> req.bucket(config.bucketName()).key(key))
        .thenApply(resp -> null);
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    client.close();
    return CompletableFuture.completedFuture(null);
  }

  private String rangeMarkersPrefix(final int partitionId) {
    return config.basePath().map(base -> base + "/").orElse("") + "ranges/" + partitionId + "/";
  }

  private CompletableFuture<List<ObjectIdentifier>> listBackupObjects(final Manifest manifest) {
    return listObjects(manifest, Directory.CONTENTS);
  }

  private CompletableFuture<List<ObjectIdentifier>> listBackupManifestObjects(
      final Manifest manifest) {
    return listObjects(manifest, Directory.MANIFESTS);
  }

  private CompletableFuture<List<ObjectIdentifier>> listObjects(
      final Manifest manifest, final Directory directory) {

    if (canDerivePathFromManifest(manifest)) {
      return listObjectsBasedOnDescriptor(manifest, directory);
    }
    return listObjectsBasedOnId(manifest, directory);
  }

  private boolean canDerivePathFromManifest(final Manifest manifest) {
    return manifest instanceof final ValidBackupManifest validManifest
        && validManifest.backupDescriptor().isPresent();
  }

  private CompletableFuture<List<ObjectIdentifier>> listObjectsBasedOnDescriptor(
      final Manifest manifest, final Directory directory) {
    final var descriptor = ((ValidBackupManifest) manifest).backupDescriptor().get();
    final var prefix = derivePath(descriptor, manifest.id(), directory);
    return listBackupObjects(prefix);
  }

  private CompletableFuture<List<ObjectIdentifier>> listObjectsBasedOnId(
      final Manifest manifest, final Directory directory) {
    final var prefix = objectPrefix(manifest.id());
    final var prefixV2 = objectPrefixV2(manifest.id(), directory);
    return listBackupObjects(prefixV2)
        .thenCombine(
            listBackupObjects(prefix),
            (v2Objects, objects) -> {
              final var allObjects = new HashSet<>(v2Objects);
              allObjects.addAll(objects);
              return allObjects.stream().toList();
            });
  }

  private CompletableFuture<List<ObjectIdentifier>> listBackupObjects(final String prefix) {
    return client
        .listObjectsV2(req -> req.bucket(config.bucketName()).prefix(prefix))
        .thenApplyAsync(
            objects ->
                objects.contents().stream()
                    .map(S3Object::key)
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList());
  }

  private CompletableFuture<Void> deleteBackupObjects(
      final Collection<ObjectIdentifier> objectIdentifiers) {
    LOG.atTrace()
        .addKeyValue("objects", objectIdentifiers::size)
        .setMessage("Deleting objects")
        .log();
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

  SdkPublisher<BackupIdentifier> findBackupIds(
      final BackupIdentifierWildcard wildcard, final boolean legacyStructure) {
    final var prefix = legacyStructure ? wildcardPrefix(wildcard) : wildcardPrefixV2(wildcard);
    LOG.debug("Using prefix {} to search for manifest files matching {}", prefix, wildcard);
    return client
        .listObjectsV2Paginator(cfg -> cfg.bucket(config.bucketName()).prefix(prefix))
        .contents()
        .filter(obj -> obj.key().endsWith(MANIFEST_OBJECT_KEY))
        .map(S3Object::key)
        .map(this::tryParseKeyAsId)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(wildcard::matches);
  }

  CompletableFuture<Collection<Manifest>> readManifestObjects(
      final BackupIdentifierWildcard wildcard) {
    final var aggregator = new AsyncAggregatingSubscriber<Manifest>(SCAN_PARALLELISM);
    final var publisher = findBackupIds(wildcard, false).map(this::readManifestObject);
    final var publisherV2 = findBackupIds(wildcard, true).map(this::readManifestObject);
    publisher.subscribe(aggregator);
    publisherV2.subscribe(aggregator);

    // Transform result to a set to remove potential duplicates between backups due to prefix
    return aggregator.result().thenApply(Set::copyOf);
  }

  private CompletableFuture<ResponseBytes<GetObjectResponse>> findManifestForBackup(
      final BackupIdentifier id) {
    return client
        .getObject(
            req ->
                req.bucket(config.bucketName())
                    .key(objectPrefixV2(id, Directory.MANIFESTS) + MANIFEST_OBJECT_KEY),
            AsyncResponseTransformer.toBytes())
        .exceptionallyCompose(
            throwable -> {
              if (throwable.getCause() instanceof NoSuchKeyException) {
                LOG.debug("Manifest not found in v2 location for backup {}, trying v1", id);
                return client.getObject(
                    req ->
                        req.bucket(config.bucketName()).key(objectPrefix(id) + MANIFEST_OBJECT_KEY),
                    AsyncResponseTransformer.toBytes());
              }
              return CompletableFuture.failedFuture(throwable);
            });
  }

  CompletableFuture<Manifest> readManifestObject(final BackupIdentifier id) {
    LOG.debug("Reading manifest object of {}", id);
    return findManifestForBackup(id)
        .thenApply(
            response -> {
              try {
                final var manifest =
                    (Manifest)
                        MAPPER.readValue(response.asInputStream(), ValidBackupManifest.class);
                return manifest;
              } catch (final IOException e) {
                throw new ManifestParseException(
                    "Failed to read manifest object: %s".formatted(response.asUtf8String()), e);
              }
            })
        .exceptionally(
            throwable -> {
              // throwable is a `CompletionException`, `getCause` to handle the underlying exception
              if (throwable.getCause() instanceof NoSuchKeyException) {
                LOG.debug("Found no manifest for backup {}", id);
                return new NoBackupManifest(BackupIdentifierImpl.from(id));
              } else if (throwable.getCause() instanceof final S3BackupStoreException e) {
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
                    .key(
                        derivePath(
                                manifest.backupDescriptor().get(),
                                manifest.id(),
                                Directory.MANIFESTS)
                            + MANIFEST_OBJECT_KEY)
                    .build(),
            body)
        .thenApply(resp -> manifest);
  }

  private CompletableFuture<FileSet> saveSnapshotFiles(final Backup backup) {
    LOG.debug("Saving snapshot files for {}", backup.id());
    final var prefix =
        derivePath(backup.descriptor(), backup.id(), Directory.CONTENTS) + SNAPSHOT_PREFIX;
    return fileSetManager.save(prefix, backup.snapshot());
  }

  private CompletableFuture<FileSet> saveSegmentFiles(final Backup backup) {
    LOG.debug("Saving segment files for {}", backup.id());
    final var prefix =
        derivePath(backup.descriptor(), backup.id(), Directory.CONTENTS) + SEGMENTS_PREFIX;
    return fileSetManager.save(prefix, backup.segments());
  }

  public String derivePath(
      final BackupDescriptor descriptor, final BackupIdentifier id, final Directory directory) {
    return isLegacyBackup(descriptor) ? objectPrefix(id) : objectPrefixV2(id, directory);
  }

  private boolean isLegacyBackup(final BackupDescriptor descriptor) {
    final var brokerVersion =
        SemanticVersion.parse(descriptor.brokerVersion())
            .orElseThrow(
                () ->
                    new ManifestParseException(
                        "Invalid broker version format in manifest: " + descriptor.brokerVersion(),
                        null));
    return brokerVersion.major() == 8 && brokerVersion.minor() <= 8;
  }

  public static S3AsyncClient buildClient(final S3BackupConfig config) {
    final var builder = S3AsyncClient.builder();

    // Enable auto-tuning of various parameters based on the environment
    builder.defaultsMode(DefaultsMode.AUTO);

    // Enable legacy MD5 plugin if configured, as it is required for compatibility with S3
    // compatible storage systems that expect MD5 checksums in the request.
    if (config.supportLegacyMd5()) {
      builder.addPlugin(LegacyMd5Plugin.create());
    }

    builder.httpClient(
        NettyNioAsyncHttpClient.builder()
            .maxConcurrency(config.maxConcurrentConnections())
            // We'd rather wait longer for a connection than have a failed backup. This helps in
            // smoothing out spikes when taking a backup.
            .connectionAcquisitionTimeout(config.connectionAcquisitionTimeout())
            .build());

    builder.overrideConfiguration(cfg -> cfg.retryStrategy(RetryMode.ADAPTIVE_V2));
    builder.forcePathStyle(config.forcePathStyleAccess());
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
    config
        .apiCallTimeout()
        .ifPresent(timeout -> builder.overrideConfiguration(cfg -> cfg.apiCallTimeout(timeout)));
    return builder.build();
  }

  public enum Directory {
    MANIFESTS("manifests"),
    CONTENTS("contents");

    private final String name;

    Directory(final String name) {
      this.name = name;
    }
  }
}
