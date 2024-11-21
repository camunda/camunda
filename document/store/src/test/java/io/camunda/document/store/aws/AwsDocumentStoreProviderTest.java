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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class AwsDocumentStoreProviderTest {

  @Test
  public void shouldCreateDocumentStore() {
    try (final var mockedFactory = mockStatic(AwsDocumentStoreFactory.class)) {
      // given
      final String bucketName = "bucketName";
      final AwsDocumentStore mockDocumentStore = mock(AwsDocumentStore.class);

      // this mock is used to bypass the auto config of S3Client.create()
      mockedFactory
          .when(() -> AwsDocumentStoreFactory.create(bucketName))
          .thenReturn(mockDocumentStore);

      final DocumentStoreConfigurationRecord configuration =
          new DocumentStoreConfigurationRecord(
              "aws", AwsDocumentStoreProvider.class, new HashMap<>());
      configuration.properties().put("BUCKET", bucketName);
      final AwsDocumentStoreProvider provider = new AwsDocumentStoreProvider();

      // when
      final DocumentStore documentStore = provider.createDocumentStore(configuration);

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
            IllegalArgumentException.class, () -> provider.createDocumentStore(configuration));
    assertThat(ex.getMessage())
        .isEqualTo(
            "Failed to configure document store with id 'my-aws': missing required property 'BUCKET'");
  }
}
