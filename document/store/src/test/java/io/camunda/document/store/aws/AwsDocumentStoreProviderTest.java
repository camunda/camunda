/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.regions.Region;

public class AwsDocumentStoreProviderTest {

  @Test
  public void shouldCreateDocumentStore() {
    try (final var mockedFactory = mockStatic(AwsDocumentStoreFactory.class)) {
      // given
      final String bucketName = "bucketName";
      final Long bucketTtl = 30L;
      final AwsDocumentStore mockDocumentStore = mock(AwsDocumentStore.class);

      // this mock is used to bypass the auto config of S3Client.create()
      mockedFactory
          .when(
              () ->
                  AwsDocumentStoreFactory.create(
                      eq(bucketName),
                      eq(bucketTtl),
                      eq(""),
                      any(),
                      eq(AwsClientOptions.sdkDefaults())))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "aws", AwsDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("BUCKET", bucketName);
      configuration.properties().put("BUCKET_TTL", String.valueOf(bucketTtl));
      final AwsDocumentStoreProvider provider = new AwsDocumentStoreProvider();

      // when
      final DocumentStore documentStore =
          provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(documentStore).isNotNull();
    }
  }

  @Test
  public void shouldThrowIfBucketNameIsMissing() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "my-aws", AwsDocumentStoreProvider.class, new HashMap<>());
    final AwsDocumentStoreProvider provider = new AwsDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .isEqualTo(
            "Failed to configure document store with id 'my-aws': missing required property 'BUCKET'");
  }

  @Test
  public void shouldThrowIfBucketTTLIsNotANumber() {
    // given
    final String bucketName = "bucketName";

    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "aws", AwsDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", bucketName);
    configuration.properties().put("BUCKET_TTL", "invalid_ttl");
    final AwsDocumentStoreProvider provider = new AwsDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .isEqualTo(
            "Failed to configure document store with id 'aws': 'BUCKET_TTL must be a number'");
  }

  @Test
  public void shouldThrowIfBucketPathIsInvalid() {
    // given

    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "aws", AwsDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucketName");
    configuration.properties().put("BUCKET_TTL", "30");
    configuration.properties().put("BUCKET_PATH", "test\\path");

    final AwsDocumentStoreProvider provider = new AwsDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .isEqualTo(
            "Failed to configure document store with id 'aws': 'BUCKET_PATH is invalid. Must not contain \\ character'");
  }

  @Test
  public void shouldPassS3CompatibleOptionsWhenEndpointSet() {
    try (final var mockedFactory = mockStatic(AwsDocumentStoreFactory.class)) {
      // given
      final AwsDocumentStore mockDocumentStore = mock(AwsDocumentStore.class);
      final ArgumentCaptor<AwsClientOptions> optionsCaptor =
          ArgumentCaptor.forClass(AwsClientOptions.class);
      mockedFactory
          .when(
              () ->
                  AwsDocumentStoreFactory.create(
                      any(), any(), any(), any(), optionsCaptor.capture()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "aws", AwsDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("BUCKET", "bucket");
      configuration.properties().put("ENDPOINT", "http://minio.local:9000");

      // when
      new AwsDocumentStoreProvider()
          .createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(optionsCaptor.getValue().endpointOverride())
          .isEqualTo(URI.create("http://minio.local:9000"));
      assertThat(optionsCaptor.getValue().forcePathStyle()).isNull();
    }
  }

  @Test
  public void shouldRespectExplicitForcePathStyleFalse() {
    try (final var mockedFactory = mockStatic(AwsDocumentStoreFactory.class)) {
      // given
      final AwsDocumentStore mockDocumentStore = mock(AwsDocumentStore.class);
      final ArgumentCaptor<AwsClientOptions> optionsCaptor =
          ArgumentCaptor.forClass(AwsClientOptions.class);
      mockedFactory
          .when(
              () ->
                  AwsDocumentStoreFactory.create(
                      any(), any(), any(), any(), optionsCaptor.capture()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "aws", AwsDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("BUCKET", "bucket");
      configuration.properties().put("ENDPOINT", "http://minio.local:9000");
      configuration.properties().put("FORCE_PATH_STYLE", "false");

      // when
      new AwsDocumentStoreProvider()
          .createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(optionsCaptor.getValue().forcePathStyle()).isFalse();
    }
  }

  @Test
  public void shouldThrowIfEndpointIsNotAValidUri() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "aws", AwsDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucket");
    configuration.properties().put("ENDPOINT", "not a uri");

    final AwsDocumentStoreProvider provider = new AwsDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .startsWith(
            "Failed to configure document store with id 'aws': 'ENDPOINT' is not a valid URI");
  }

  @Test
  public void shouldRespectExplicitChunkedEncodingDisabled() {
    try (final var mockedFactory = mockStatic(AwsDocumentStoreFactory.class)) {
      // given
      final AwsDocumentStore mockDocumentStore = mock(AwsDocumentStore.class);
      final ArgumentCaptor<AwsClientOptions> optionsCaptor =
          ArgumentCaptor.forClass(AwsClientOptions.class);
      mockedFactory
          .when(
              () ->
                  AwsDocumentStoreFactory.create(
                      any(), any(), any(), any(), optionsCaptor.capture()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "aws", AwsDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("BUCKET", "bucket");
      configuration.properties().put("ENDPOINT", "http://garage.local:3900");
      configuration.properties().put("CHUNKED_ENCODING_ENABLED", "false");

      // when
      new AwsDocumentStoreProvider()
          .createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(optionsCaptor.getValue().chunkedEncodingEnabled()).isFalse();
    }
  }

  @Test
  public void shouldPassConfiguredRegion() {
    try (final var mockedFactory = mockStatic(AwsDocumentStoreFactory.class)) {
      // given
      final AwsDocumentStore mockDocumentStore = mock(AwsDocumentStore.class);
      final ArgumentCaptor<AwsClientOptions> optionsCaptor =
          ArgumentCaptor.forClass(AwsClientOptions.class);
      mockedFactory
          .when(
              () ->
                  AwsDocumentStoreFactory.create(
                      any(), any(), any(), any(), optionsCaptor.capture()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "aws", AwsDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("BUCKET", "bucket");
      configuration.properties().put("REGION", "eu-central-1");

      // when
      new AwsDocumentStoreProvider()
          .createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(optionsCaptor.getValue().region()).isEqualTo("eu-central-1");
    }
  }

  @Test
  public void shouldLeaveRegionToTheSdkWhenNotConfigured() {
    try (final var mockedFactory = mockStatic(AwsDocumentStoreFactory.class)) {
      // given
      final AwsDocumentStore mockDocumentStore = mock(AwsDocumentStore.class);
      final ArgumentCaptor<AwsClientOptions> optionsCaptor =
          ArgumentCaptor.forClass(AwsClientOptions.class);
      mockedFactory
          .when(
              () ->
                  AwsDocumentStoreFactory.create(
                      any(), any(), any(), any(), optionsCaptor.capture()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "aws", AwsDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("BUCKET", "bucket");
      configuration.properties().put("REGION", "  ");

      // when
      new AwsDocumentStoreProvider()
          .createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(optionsCaptor.getValue().region()).isNull();
    }
  }

  @Test
  public void shouldTargetTheConfiguredRegionFromClientAndPresigner() {
    // given
    final AwsClientOptions options =
        new AwsClientOptions(null, null, null, null, "eu-central-1", null, null);

    // when
    try (final var client = AwsDocumentStore.buildClient(options);
        final var presigner = AwsDocumentStore.buildPresigner(options)) {

      // then
      assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.of("eu-central-1"));
      // the presigner does not expose its region; it only builds at all once one is resolvable
      assertThat(presigner).isNotNull();
    }
  }

  @Test
  public void shouldPassConfiguredCredentials() {
    try (final var mockedFactory = mockStatic(AwsDocumentStoreFactory.class)) {
      // given
      final AwsDocumentStore mockDocumentStore = mock(AwsDocumentStore.class);
      final ArgumentCaptor<AwsClientOptions> optionsCaptor =
          ArgumentCaptor.forClass(AwsClientOptions.class);
      mockedFactory
          .when(
              () ->
                  AwsDocumentStoreFactory.create(
                      any(), any(), any(), any(), optionsCaptor.capture()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "aws", AwsDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("BUCKET", "bucket");
      configuration.properties().put("ACCESS_KEY", "tenant-a-key");
      configuration.properties().put("SECRET_KEY", "tenant-a-secret");

      // when
      new AwsDocumentStoreProvider()
          .createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(optionsCaptor.getValue().accessKey()).isEqualTo("tenant-a-key");
      assertThat(optionsCaptor.getValue().secretKey()).isEqualTo("tenant-a-secret");
      assertThat(optionsCaptor.getValue().hasStaticCredentials()).isTrue();
    }
  }

  @Test
  public void shouldFallBackToTheSdkCredentialChainWhenNoKeyPairIsConfigured() {
    try (final var mockedFactory = mockStatic(AwsDocumentStoreFactory.class)) {
      // given
      final AwsDocumentStore mockDocumentStore = mock(AwsDocumentStore.class);
      final ArgumentCaptor<AwsClientOptions> optionsCaptor =
          ArgumentCaptor.forClass(AwsClientOptions.class);
      mockedFactory
          .when(
              () ->
                  AwsDocumentStoreFactory.create(
                      any(), any(), any(), any(), optionsCaptor.capture()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "aws", AwsDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("BUCKET", "bucket");

      // when
      new AwsDocumentStoreProvider()
          .createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(optionsCaptor.getValue().hasStaticCredentials()).isFalse();
      assertThat(optionsCaptor.getValue().credentialsProvider()).isNull();
    }
  }

  @Test
  public void shouldThrowIfOnlyAccessKeyIsConfigured() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "my-aws", AwsDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucket");
    configuration.properties().put("ACCESS_KEY", "tenant-a-key");

    final AwsDocumentStoreProvider provider = new AwsDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .startsWith(
            "Failed to configure document store with id 'my-aws': 'ACCESS_KEY' and 'SECRET_KEY' must be configured together");
  }

  @Test
  public void shouldThrowIfOnlySecretKeyIsConfigured() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "my-aws", AwsDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucket");
    configuration.properties().put("SECRET_KEY", "tenant-a-secret");

    final AwsDocumentStoreProvider provider = new AwsDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .startsWith(
            "Failed to configure document store with id 'my-aws': 'ACCESS_KEY' and 'SECRET_KEY' must be configured together");
  }

  @Test
  public void shouldSignWithTheConfiguredCredentials() {
    // given
    final AwsClientOptions options =
        new AwsClientOptions(
            null, null, null, null, "eu-central-1", "tenant-a-key", "tenant-a-secret");

    // when
    try (final var client = AwsDocumentStore.buildClient(options)) {

      // then
      final var credentials =
          client.serviceClientConfiguration().credentialsProvider().resolveIdentity().join();
      assertThat(credentials.accessKeyId()).isEqualTo("tenant-a-key");
      assertThat(credentials.secretAccessKey()).isEqualTo("tenant-a-secret");
    }
  }

  @Test
  public void shouldNotExposeEitherHalfOfTheKeyPairWhenPrinted() {
    // given
    final AwsClientOptions options =
        new AwsClientOptions(
            null, null, null, null, "eu-central-1", "tenant-a-key", "tenant-a-secret");

    // when
    final String printed = options.toString();

    // then
    assertThat(printed).contains("eu-central-1").contains("<redacted>");
    assertThat(printed).doesNotContain("tenant-a-secret").doesNotContain("tenant-a-key");
  }

  @Test
  public void shouldLeaveEverySettingToTheSdkWhenNothingIsOverridden() {
    // a store with no overrides must keep taking the S3Client.create() / S3Presigner.create() path,
    // where the SDK resolves region and credentials from AWS_REGION, an instance profile, and the
    // rest of the default chain — the path every deployment without per-store credentials still
    // uses, and the one no integration test can exercise
    assertThat(AwsClientOptions.sdkDefaults().usesSdkDefaults()).isTrue();
  }

  @Test
  public void shouldNotFallBackToTheSdkWhenAnySingleSettingIsOverridden() {
    // given / when / then — each override alone is enough to take the builder path; a store that
    // configures only its credentials, or only its region, must not silently address the process
    // environment instead
    assertThat(
            new AwsClientOptions(
                    URI.create("http://minio.local:9000"), null, null, null, null, null, null)
                .usesSdkDefaults())
        .isFalse();
    assertThat(new AwsClientOptions(null, true, null, null, null, null, null).usesSdkDefaults())
        .isFalse();
    assertThat(new AwsClientOptions(null, null, false, null, null, null, null).usesSdkDefaults())
        .isFalse();
    assertThat(
            new AwsClientOptions(null, null, null, null, "eu-central-1", null, null)
                .usesSdkDefaults())
        .isFalse();
    assertThat(
            new AwsClientOptions(null, null, null, null, null, "tenant-a-key", "tenant-a-secret")
                .usesSdkDefaults())
        .isFalse();
  }

  @Test
  public void shouldThrowIfRegionIsNotAValidRegion() {
    // given — Region.of accepts any string, so an unchecked region would only surface at the first
    // document operation as an unresolvable host
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "my-aws", AwsDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucket");
    configuration.properties().put("REGION", "EU West 1");

    final AwsDocumentStoreProvider provider = new AwsDocumentStoreProvider();

    // when / then
    final var ex =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    provider.createDocumentStore(
                        configuration, Executors.newSingleThreadExecutor()))
            .actual();
    assertThat(ex.getMessage())
        .isEqualTo(
            "Failed to configure document store with id 'my-aws': 'REGION' is not a valid region: EU West 1");
  }

  @Test
  public void shouldAcceptARegionTheSdkDoesNotKnow() {
    try (final var mockedFactory = mockStatic(AwsDocumentStoreFactory.class)) {
      // given — a region newer than the bundled SDK, or an S3-compatible backend's own region name,
      // must still be accepted; only a structurally impossible one is rejected
      final AwsDocumentStore mockDocumentStore = mock(AwsDocumentStore.class);
      final ArgumentCaptor<AwsClientOptions> optionsCaptor =
          ArgumentCaptor.forClass(AwsClientOptions.class);
      mockedFactory
          .when(
              () ->
                  AwsDocumentStoreFactory.create(
                      any(), any(), any(), any(), optionsCaptor.capture()))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "aws", AwsDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("BUCKET", "bucket");
      configuration.properties().put("REGION", "xx-fictional-9");

      // when
      new AwsDocumentStoreProvider()
          .createDocumentStore(configuration, Executors.newSingleThreadExecutor());

      // then
      assertThat(optionsCaptor.getValue().region()).isEqualTo("xx-fictional-9");
    }
  }
}
