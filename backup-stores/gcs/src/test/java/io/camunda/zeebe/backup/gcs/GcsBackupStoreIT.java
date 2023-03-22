/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.cloud.storage.BucketInfo;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.gcs.util.GcsContainer;
import io.camunda.zeebe.backup.testkit.DeletingBackup;
import io.camunda.zeebe.backup.testkit.ListingBackups;
import io.camunda.zeebe.backup.testkit.QueryingBackupStatus;
import io.camunda.zeebe.backup.testkit.SavingBackup;
import io.camunda.zeebe.backup.testkit.UpdatingBackupStatus;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class GcsBackupStoreIT
    implements SavingBackup,
        UpdatingBackupStatus,
        QueryingBackupStatus,
        ListingBackups,
        DeletingBackup {
  @Container private static final GcsContainer GCS = new GcsContainer();
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  private GcsBackupStore store;

  @BeforeAll
  static void createBucket() {
    final var config =
        new GcsBackupConfig.Builder()
            .withBucketName(BUCKET_NAME)
            .withBasePath(RandomStringUtils.randomAlphabetic(10).toLowerCase())
            .withHost(GCS.externalEndpoint())
            .withoutAuthentication()
            .build();
    try (final var client = GcsBackupStore.buildClient(config)) {
      client.create(BucketInfo.of(BUCKET_NAME));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeEach
  void setup() {
    store =
        new GcsBackupStore(
            new GcsBackupConfig.Builder()
                .withBucketName(BUCKET_NAME)
                .withBasePath(RandomStringUtils.randomAlphabetic(10).toLowerCase())
                .withHost(GCS.externalEndpoint())
                .withoutAuthentication()
                .build());
  }

  @Override
  public BackupStore getStore() {
    return store;
  }

  @Override
  public Class<? extends Exception> getBackupInInvalidStateExceptionClass() {
    return UnexpectedManifestState.class;
  }
}
