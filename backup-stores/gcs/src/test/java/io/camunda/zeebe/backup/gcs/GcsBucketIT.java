/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.gcs.util.GcsContainer;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class GcsBucketIT {

  @Container private static final GcsContainer GCS = new GcsContainer();
  private String bucketName;
  private GcsBackupStore store;
  private Storage client;

  @BeforeEach
  void setup() {
    bucketName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    final var config =
        new GcsBackupConfig.Builder()
            .withBucketName(bucketName)
            .withHost(GCS.externalEndpoint())
            .withoutAuthentication()
            .build();
    client = GcsBackupStore.buildClient(config);
    client.create(BucketInfo.of(bucketName));

    store =
        new GcsBackupStore(
            new GcsBackupConfig.Builder()
                .withBucketName(bucketName)
                .withHost(GCS.externalEndpoint())
                .withoutAuthentication()
                .build());
  }

  @Test
  public void shouldStoreBackupInCorrectPaths() throws IOException {
    // given
    final File snapshotFile1 = File.createTempFile("file1", "snapshotFile1");
    final File snapshotFile2 = File.createTempFile("file2", "snapshotFile2");
    final File segmentFile = File.createTempFile("file3", "segmentFile1");
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(Optional.empty(), 1, 1, "version"),
            new NamedFileSetImpl(
                Map.of(
                    "snapshotFile1",
                    snapshotFile1.toPath(),
                    "snapshotFile2",
                    snapshotFile2.toPath())),
            new NamedFileSetImpl(Map.of("segmentFile1", segmentFile.toPath())));

    // when
    store.save(backup).join();

    // then
    final Page<Blob> blobList = client.list(bucketName);

    Assertions.assertThat(blobList).isNotNull();

    final var blobs = new ArrayList<Blob>();
    blobList.iterateAll().forEach(blobs::add);

    Assertions.assertThat(blobs).isNotEmpty();
    Assertions.assertThat(blobs)
        .extracting(Blob::getName)
        .containsExactlyInAnyOrder(
            "contents/2/3/1/segments/segmentFile1",
            "contents/2/3/1/snapshot/snapshotFile1",
            "contents/2/3/1/snapshot/snapshotFile2",
            "manifests/2/3/1/manifest.json");
  }

  @Test
  public void shouldStoreBackupInCorrectPathsUnderBase() throws IOException {
    // given
    store =
        new GcsBackupStore(
            new GcsBackupConfig.Builder()
                .withBucketName(bucketName)
                .withHost(GCS.externalEndpoint())
                .withBasePath("root")
                .withoutAuthentication()
                .build());
    final File snapshotFile1 = File.createTempFile("file1", "snapshotFile1");
    final File snapshotFile2 = File.createTempFile("file2", "snapshotFile2");
    final File segmentFile = File.createTempFile("file3", "segmentFile1");
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(Optional.empty(), 1, 1, "version"),
            new NamedFileSetImpl(
                Map.of(
                    "snapshotFile1",
                    snapshotFile1.toPath(),
                    "snapshotFile2",
                    snapshotFile2.toPath())),
            new NamedFileSetImpl(Map.of("segmentFile1", segmentFile.toPath())));

    // when
    store.save(backup).join();

    // then
    final Page<Blob> blobList = client.list(bucketName);

    Assertions.assertThat(blobList).isNotNull();

    final var blobs = new ArrayList<Blob>();
    blobList.iterateAll().forEach(blobs::add);

    Assertions.assertThat(blobs).isNotEmpty();
    Assertions.assertThat(blobs)
        .extracting(Blob::getName)
        .containsExactlyInAnyOrder(
            "root/contents/2/3/1/segments/segmentFile1",
            "root/contents/2/3/1/snapshot/snapshotFile1",
            "root/contents/2/3/1/snapshot/snapshotFile2",
            "root/manifests/2/3/1/manifest.json");
  }
}
