/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class ConfigTest {

  @Test
  void shouldRejectMissingBucketName() {
    // given
    final String bucketName = null;
    // when
    final var config = new GcsBackupConfig.Builder().withBucketName(bucketName);

    // then
    Assertions.assertThatThrownBy(config::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bucketName");
  }

  @Test
  void shouldRejectEmptyBucketName() {
    // given
    final var bucketName = "";
    // when
    final var config = new GcsBackupConfig.Builder().withBucketName(bucketName);

    // then
    Assertions.assertThatThrownBy(config::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bucketName");
  }

  @Test
  void shouldRemoveLeadingSlashesFromBasePath() {
    // given
    final var bucketName = "test";
    final var basePath = "/tenant";
    // when
    final var config =
        new GcsBackupConfig.Builder().withBucketName(bucketName).withBasePath(basePath).build();

    // then
    Assertions.assertThat(config.basePath()).isEqualTo("tenant");
  }

  @Test
  void shouldRemoveTrailingSlashesFromBasePath() {
    // given
    final var bucketName = "test";
    final var basePath = "/tenants/abc/";
    // when
    final var config =
        new GcsBackupConfig.Builder().withBucketName(bucketName).withBasePath(basePath).build();

    // then
    Assertions.assertThat(config.basePath()).isEqualTo("tenants/abc");
  }

  @Test
  void shouldRejectBasePathConsistingOfOnlySlashes() {
    // given
    final var bucketName = "test";
    final var basePath = "//";
    // when
    final var config =
        new GcsBackupConfig.Builder().withBucketName(bucketName).withBasePath(basePath);

    // then
    Assertions.assertThatThrownBy(config::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("basePath");
  }
}
