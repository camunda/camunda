/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Testcontainers
final class S3BackupStoreIT {

  @Container
  LocalStackContainer localStack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:0.14.5"))
          .withServices(Service.S3);

  private S3AsyncClient client;
  private S3BackupConfig config;
  private S3BackupStore store;

  @BeforeEach
  void setupBucket() {
    client =
        S3AsyncClient.builder()
            .endpointOverride(localStack.getEndpointOverride(Service.S3))
            .region(Region.of(localStack.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localStack.getAccessKey(), localStack.getSecretKey())))
            .build();

    config = new S3BackupConfig(RandomStringUtils.randomAlphabetic(10).toLowerCase());
    store = new S3BackupStore(config, client);

    client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
  }

  @Test
  void savesMetadata() throws IOException {
    // given
    final var backup =
        new TestBackup(
            new TestBackupIdentifier(1, 2, 3),
            new TestBackupDescriptor(4, 5, "test-snapshot-id"),
            new TestNamedFileSet(
                Map.of(
                    "segment-file-1",
                    Path.of("/local-segments/segment-file-1"),
                    "segment-file-2",
                    Path.of("/local-segments/segment-file-2"))),
            new TestNamedFileSet(
                Map.of(
                    "snapshot-file-1",
                    Path.of("/local-snapshot/snapshot-file-1"),
                    "snapshot-file-2",
                    Path.of("/local-snapshot/snapshot-file-2"))));

    // when
    final var result = store.save(backup);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(10));

    final var metadataObject =
        client
            .getObject(
                GetObjectRequest.builder()
                    .bucket(config.bucketName())
                    .key("2/1/3/metadata")
                    .build(),
                AsyncResponseTransformer.toBytes())
            .join();

    final var objectMapper = new ObjectMapper();
    final var readMetadata = objectMapper.readValue(metadataObject.asByteArray(), Metadata.class);

    assertThat(readMetadata.checkpointId()).isEqualTo(backup.id.checkpointId);
    assertThat(readMetadata.partitionId()).isEqualTo(backup.id.partitionId);
    assertThat(readMetadata.nodeId()).isEqualTo(backup.id.nodeId);

    assertThat(readMetadata.checkpointPosition()).isEqualTo(backup.descriptor.checkpointPosition);
    assertThat(readMetadata.snapshotId()).isEqualTo(backup.descriptor.snapshotId);
    assertThat(readMetadata.numberOfPartitions()).isEqualTo(backup.descriptor.numberOfPartitions);

    assertThat(readMetadata.snapshotFileNames()).isEqualTo(backup.snapshot.names());
    assertThat(readMetadata.segmentFileNames()).isEqualTo(backup.segments.names());
  }

  record TestBackup(
      TestBackupIdentifier id,
      TestBackupDescriptor descriptor,
      TestNamedFileSet segments,
      TestNamedFileSet snapshot)
      implements Backup {}

  record TestBackupIdentifier(long checkpointId, int partitionId, int nodeId)
      implements BackupIdentifier {}

  record TestBackupDescriptor(long checkpointPosition, int numberOfPartitions, String snapshotId)
      implements BackupDescriptor {}

  record TestNamedFileSet(Map<String, Path> namedFiles) implements NamedFileSet {

    @Override
    public Set<String> names() {
      return namedFiles.keySet();
    }

    @Override
    public Set<Path> files() {
      return Set.copyOf(namedFiles.values());
    }
  }
}
