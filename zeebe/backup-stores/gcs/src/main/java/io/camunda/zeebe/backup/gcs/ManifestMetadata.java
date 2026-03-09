/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.cloud.storage.Blob;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Converts backup manifest information to and from GCS blob custom metadata. This allows listing
 * backup statuses without downloading the manifest content, eliminating one GET request per backup.
 */
final class ManifestMetadata {

  static final String STATUS_CODE = "status-code";
  static final String FAILURE_REASON = "failure-reason";
  static final String CREATED_AT = "created-at";
  static final String MODIFIED_AT = "modified-at";
  static final String SNAPSHOT_ID = "snapshot-id";
  static final String FIRST_LOG_POSITION = "first-log-position";
  static final String CHECKPOINT_POSITION = "checkpoint-position";
  static final String NUMBER_OF_PARTITIONS = "number-of-partitions";
  static final String BROKER_VERSION = "broker-version";
  static final String CHECKPOINT_TIMESTAMP = "checkpoint-timestamp";
  static final String CHECKPOINT_TYPE = "checkpoint-type";

  private ManifestMetadata() {}

  static Map<String, String> fromManifest(final Manifest manifest) {
    final var metadata = new HashMap<String, String>();
    metadata.put(STATUS_CODE, manifest.statusCode().name());

    if (manifest.createdAt() != null) {
      metadata.put(CREATED_AT, manifest.createdAt().toString());
    }
    if (manifest.modifiedAt() != null) {
      metadata.put(MODIFIED_AT, manifest.modifiedAt().toString());
    }

    if (manifest.statusCode() == Manifest.StatusCode.FAILED) {
      final var failureReason = manifest.asFailed().failureReason();
      if (failureReason != null) {
        metadata.put(FAILURE_REASON, failureReason);
      }
    }

    final var descriptor = manifest.descriptor();
    if (descriptor != null) {
      descriptor.snapshotId().ifPresent(id -> metadata.put(SNAPSHOT_ID, id));
      final var firstLogPos = descriptor.firstLogPosition();
      if (firstLogPos.isPresent()) {
        metadata.put(FIRST_LOG_POSITION, Long.toString(firstLogPos.getAsLong()));
      }
      metadata.put(CHECKPOINT_POSITION, Long.toString(descriptor.checkpointPosition()));
      metadata.put(NUMBER_OF_PARTITIONS, Integer.toString(descriptor.numberOfPartitions()));
      metadata.put(BROKER_VERSION, descriptor.brokerVersion());
      if (descriptor.checkpointTimestamp() != null) {
        metadata.put(CHECKPOINT_TIMESTAMP, descriptor.checkpointTimestamp().toString());
      }
      if (descriptor.checkpointType() != null) {
        metadata.put(CHECKPOINT_TYPE, descriptor.checkpointType().name());
      }
    }

    return metadata;
  }

  /**
   * Reconstructs a {@link BackupStatus} from the GCS blob's custom metadata and path. The blob path
   * encodes the backup identifier (partitionId/checkpointId/nodeId).
   *
   * @return the backup status, or empty if the blob has no status metadata (e.g. written by an
   *     older version)
   */
  static Optional<BackupStatus> toBackupStatus(
      final Blob blob, final String basePath, final String manifestBlobName) {
    final var metadata = blob.getMetadata();
    if (metadata == null || !metadata.containsKey(STATUS_CODE)) {
      return Optional.empty();
    }

    final var id = parseIdentifierFromPath(blob.getName(), basePath, manifestBlobName);
    final var statusCode = BackupStatusCode.valueOf(metadata.get(STATUS_CODE));
    final var failureReason = Optional.ofNullable(metadata.get(FAILURE_REASON));
    final var created = Optional.ofNullable(metadata.get(CREATED_AT)).map(Instant::parse);
    final var modified = Optional.ofNullable(metadata.get(MODIFIED_AT)).map(Instant::parse);
    final var descriptor = parseDescriptor(metadata);

    return Optional.of(
        new BackupStatusImpl(id, descriptor, statusCode, failureReason, created, modified));
  }

  private static BackupIdentifier parseIdentifierFromPath(
      final String blobName, final String basePath, final String manifestBlobName) {
    // Path format: {basePath}manifests/{partitionId}/{checkpointId}/{nodeId}/manifest.json
    final var manifestsPrefix = basePath + "manifests/";
    final var relativePath =
        blobName.substring(manifestsPrefix.length(), blobName.length() - manifestBlobName.length());
    // relativePath is now: {partitionId}/{checkpointId}/{nodeId}/
    final var parts = relativePath.split("/");
    return new BackupIdentifierImpl(
        Integer.parseInt(parts[2]), Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
  }

  private static Optional<BackupDescriptor> parseDescriptor(final Map<String, String> metadata) {
    final var checkpointPositionStr = metadata.get(CHECKPOINT_POSITION);
    if (checkpointPositionStr == null) {
      return Optional.empty();
    }

    final var checkpointType =
        Optional.ofNullable(metadata.get(CHECKPOINT_TYPE))
            .map(CheckpointType::valueOf)
            .orElse(CheckpointType.MANUAL_BACKUP);

    return Optional.of(
        new BackupDescriptorImpl(
            Optional.ofNullable(metadata.get(SNAPSHOT_ID)),
            metadata.containsKey(FIRST_LOG_POSITION)
                ? OptionalLong.of(Long.parseLong(metadata.get(FIRST_LOG_POSITION)))
                : OptionalLong.empty(),
            Long.parseLong(checkpointPositionStr),
            Integer.parseInt(metadata.get(NUMBER_OF_PARTITIONS)),
            metadata.get(BROKER_VERSION),
            Optional.ofNullable(metadata.get(CHECKPOINT_TIMESTAMP))
                .map(Instant::parse)
                .orElse(null),
            checkpointType));
  }
}
