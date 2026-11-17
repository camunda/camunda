/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.util.S3TestBackupProvider;
import io.camunda.zeebe.test.testcontainers.S3MockTestContainer;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.LegacyMd5Plugin;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;

@Testcontainers
public class S3MockLegacyMd5IT implements S3BackupStoreTests {
  private static final Logger LOG = LoggerFactory.getLogger(S3MockLegacyMd5IT.class);
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container private static final S3MockTestContainer S3 = new S3MockTestContainer();

  private S3AsyncClient client;
  private S3BackupStore store;
  private S3BackupConfig config;

  @BeforeAll
  static void setupBucket() {
    final var config =
        new Builder()
            .withBucketName(BUCKET_NAME)
            .withEndpoint(S3.externalEndpoint())
            .withRegion(S3.region())
            .withCredentials(S3.accessKey(), S3.secretKey())
            .forcePathStyleAccess(true)
            .build();
    try (final var client = S3BackupStore.buildClient(config)) {
      client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
    }
  }

  @BeforeEach
  void setup(final TestInfo testInfo) {
    final String basePath = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    config =
        new Builder()
            .withBucketName(BUCKET_NAME)
            .withBasePath(basePath)
            .withEndpoint(S3.externalEndpoint())
            .withRegion(S3.region())
            .withCredentials(S3.accessKey(), S3.secretKey())
            .forcePathStyleAccess(true)
            .withSupportLegacyMd5(true)
            .build();
    client = S3BackupStore.buildClient(config);
    store = new S3BackupStore(config, client);

    LOG.info("{} is running with base path {}", testInfo.getDisplayName(), basePath);
  }

  @AfterEach
  void tearDown() {
    store.closeAsync();
  }

  @Test
  void shouldSendContentMd5HeaderOnDeleteObjects() throws IOException {
    // given
    final var interceptor = new ContentMd5AssertingInterceptor();
    try (final var interceptingClient = buildClientWithInterceptor(config, interceptor)) {
      final var interceptingStore = new S3BackupStore(config, interceptingClient);
      try {
        final var backup =
            S3TestBackupProvider.simpleBackupWithId(
                new BackupIdentifierImpl(1, 2, 3), VersionUtil.getVersion());
        interceptingStore.save(backup).join();
        interceptingStore.markDeleted(backup.id()).join();

        // when
        interceptingStore.delete(backup.id()).join();

        // then
        assertThat(interceptor.deleteObjectsRequestCount()).isPositive();
      } finally {
        interceptingStore.closeAsync().join();
      }
    }
  }

  private static S3AsyncClient buildClientWithInterceptor(
      final S3BackupConfig config, final ExecutionInterceptor interceptor) {
    final var builder = S3AsyncClient.builder();
    builder.defaultsMode(DefaultsMode.AUTO);
    if (config.supportLegacyMd5()) {
      builder.addPlugin(LegacyMd5Plugin.create());
    }
    builder.overrideConfiguration(
        cfg -> {
          cfg.retryStrategy(RetryMode.ADAPTIVE_V2);
          cfg.addExecutionInterceptor(interceptor);
        });
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
    return builder.build();
  }

  @Override
  public S3AsyncClient getClient() {
    return client;
  }

  @Override
  public S3BackupConfig getConfig() {
    return config;
  }

  @Override
  public S3BackupStore getStore() {
    return store;
  }

  @Override
  public Class<? extends Exception> getFileNotFoundExceptionClass() {
    return NoSuchFileException.class;
  }

  // Only delete requests are checked because uploads no longer send this header, even with
  // LegacyMd5Plugin enabled.
  private static final class ContentMd5AssertingInterceptor implements ExecutionInterceptor {
    private static final String CONTENT_MD5_HEADER = "Content-MD5";

    private final AtomicInteger deleteObjectsRequestCount = new AtomicInteger();

    @Override
    public void beforeTransmission(
        final Context.BeforeTransmission context, final ExecutionAttributes executionAttributes) {
      final SdkRequest request = context.request();
      if (request instanceof DeleteObjectsRequest) {
        deleteObjectsRequestCount.incrementAndGet();
        final var headers = context.httpRequest().headers();
        if (!headers.containsKey(CONTENT_MD5_HEADER)) {
          throw new AssertionError(
              "Expected DeleteObjects request to carry a %s header, but it did not. Headers present: %s"
                  .formatted(CONTENT_MD5_HEADER, headers.keySet()));
        }
      }
    }

    int deleteObjectsRequestCount() {
      return deleteObjectsRequestCount.get();
    }
  }
}
