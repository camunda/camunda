/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class GcpDocumentStoreProviderTest {

  @Test
  public void shouldCreateDocumentStore() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "gcp", GcpDocumentStoreProvider.class, new HashMap<>());
    configuration.properties().put("BUCKET", "bucketName");
    final GcpDocumentStoreProvider provider = new GcpDocumentStoreProvider();

    // when
    final DocumentStore documentStore = provider.createDocumentStore(configuration);

    // then
    assertNotNull(documentStore);
  }

  @Test
  public void shouldThrowIfBucketNameIsMissing() {
    // given
    final DocumentStoreConfigurationRecord configuration =
        new DocumentStoreConfigurationRecord(
            "my-gcp", GcpDocumentStoreProvider.class, new HashMap<>());
    final GcpDocumentStoreProvider provider = new GcpDocumentStoreProvider();

    // when / then
    final var ex =
        assertThrows(
            IllegalArgumentException.class, () -> provider.createDocumentStore(configuration));
    assertThat(ex.getMessage())
        .isEqualTo(
            "Failed to configure document store with id 'my-gcp': missing required property 'BUCKET'");
  }
}
