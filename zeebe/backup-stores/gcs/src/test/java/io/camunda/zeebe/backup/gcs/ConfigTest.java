/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.StorageOptions;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.ConfigurationException;
import io.camunda.zeebe.backup.gcs.GcsConnectionConfig.Authentication.Auto;
import java.io.IOException;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
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
        .isInstanceOf(ConfigurationException.class)
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
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("bucketName");
  }

  @Test
  void shouldAcceptSingleSlashAsBasePath() {
    // given
    final var bucketName = "test";
    final var basePath = "/";

    // when
    final var config =
        new GcsBackupConfig.Builder().withBucketName(bucketName).withBasePath(basePath).build();

    // then
    Assertions.assertThat(config.basePath()).isNull();
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
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("basePath");
  }

  @Test
  void shouldUseDefaultApplicationCredentialsByDefault() {
    // given
    final var bucketName = "test";

    // when
    final var config = new GcsBackupConfig.Builder().withBucketName(bucketName).build();

    // then
    Assertions.assertThat(config.connection().auth()).isInstanceOf(Auto.class);
  }

  @Test
  void shouldUseConfiguredReadTimeoutAsClientReadTimeout() {
    // given
    final var config =
        new GcsBackupConfig.Builder()
            .withBucketName("test")
            .withoutAuthentication()
            .withReadTimeout(Duration.ofSeconds(90))
            .build();

    // when
    final var client = GcsBackupStore.buildClient(config);

    // then
    Assertions.assertThat(client.getOptions().getTransportOptions())
        .asInstanceOf(InstanceOfAssertFactories.type(HttpTransportOptions.class))
        .extracting(HttpTransportOptions::getReadTimeout)
        .isEqualTo(90_000);
  }

  @Test
  void shouldApplyConfiguredWriteTimeoutToRequests() throws IOException {
    // given
    final var config =
        new GcsBackupConfig.Builder()
            .withBucketName("test")
            .withoutAuthentication()
            .withWriteTimeout(Duration.ofSeconds(120))
            .build();
    final var transportOptions = GcsBackupStore.transportOptions(config).orElseThrow();
    final var request =
        new NetHttpTransport()
            .createRequestFactory()
            .buildGetRequest(new GenericUrl("http://localhost"));

    // when
    // the write timeout is not part of HttpTransportOptions, it is applied per request
    transportOptions
        .getHttpRequestInitializer(StorageOptions.newBuilder().setProjectId("test").build())
        .initialize(request);

    // then
    Assertions.assertThat(request.getWriteTimeout()).isEqualTo(120_000);
  }

  @Test
  void shouldKeepClientDefaultsWhenNoTimeoutIsSet() {
    // given
    final var config =
        new GcsBackupConfig.Builder().withBucketName("test").withoutAuthentication().build();

    // when
    final var client = GcsBackupStore.buildClient(config);

    // then
    // -1 means the client does not set a read timeout, leaving the google-http-client default
    Assertions.assertThat(GcsBackupStore.transportOptions(config)).isEmpty();
    Assertions.assertThat(client.getOptions().getTransportOptions())
        .asInstanceOf(InstanceOfAssertFactories.type(HttpTransportOptions.class))
        .extracting(HttpTransportOptions::getReadTimeout)
        .isEqualTo(-1);
  }

  @Test
  void shouldUseNoAuthenticationWhenRequested() {
    // given
    final var bucketName = "test";

    // when
    final var config =
        new GcsBackupConfig.Builder().withBucketName(bucketName).withoutAuthentication().build();

    // then
    Assertions.assertThat(config.connection().auth())
        .isInstanceOf(GcsConnectionConfig.Authentication.None.class);
  }
}
