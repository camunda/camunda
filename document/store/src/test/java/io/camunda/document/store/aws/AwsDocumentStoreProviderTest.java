/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import java.util.HashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

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
          .when(() -> AwsDocumentStoreFactory.create(eq(bucketName), eq(bucketTtl), eq(""), any()))
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
      assertNotNull(documentStore);
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
        assertThrows(
            IllegalArgumentException.class,
            () -> provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor()));
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
        assertThrows(
            IllegalArgumentException.class,
            () -> provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor()));
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
        assertThrows(
            IllegalArgumentException.class,
            () -> provider.createDocumentStore(configuration, Executors.newSingleThreadExecutor()));
    assertThat(ex.getMessage())
        .isEqualTo(
            "Failed to configure document store with id 'aws': 'BUCKET_PATH is invalid. Must not contain \\ character'");
  }
}
