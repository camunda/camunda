/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.MetadataStore.SCHEMA_VERSION_METADATA_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.schema.indices.MetadataIndex;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchIndexOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.IndexRequest;

@ExtendWith(MockitoExtension.class)
public class OpensearchMetadataStoreTest {

  private static final String INDEX_NAME = "operate-metadata-8.8.0";
  private static final String SCHEMA_VERSION = "8.7.99";

  @Mock private RichOpenSearchClient richOpenSearchClient;

  @Mock private OpenSearchIndexOperations indexOperations;

  @Mock private OpenSearchDocumentOperations documentOperations;

  @Mock private MetadataIndex metadataIndex;

  @InjectMocks private OpensearchMetadataStore underTest;

  @BeforeEach
  public void setup() {
    when(metadataIndex.getFullQualifiedName()).thenReturn(INDEX_NAME);
  }

  @Test
  public void testGetSchemaVersionWhenIndexDoesNotExist() {
    // given
    when(richOpenSearchClient.index()).thenReturn(indexOperations);
    when(indexOperations.indexExists(INDEX_NAME)).thenReturn(false);

    // when
    final String result = underTest.getSchemaVersion();

    // then
    assertThat(result).isNull();
    verify(indexOperations).indexExists(INDEX_NAME);
  }

  @Test
  public void testGetSchemaVersionWhenDocumentDoesNotExist() {
    // given
    when(richOpenSearchClient.index()).thenReturn(indexOperations);
    when(richOpenSearchClient.doc()).thenReturn(documentOperations);
    when(indexOperations.indexExists(INDEX_NAME)).thenReturn(true);
    when(documentOperations.documentExistsWithRetries(INDEX_NAME, SCHEMA_VERSION_METADATA_ID))
        .thenReturn(false);

    // when
    final String result = underTest.getSchemaVersion();

    // then
    assertThat(result).isNull();
    verify(indexOperations).indexExists(INDEX_NAME);
    verify(documentOperations).documentExistsWithRetries(INDEX_NAME, SCHEMA_VERSION_METADATA_ID);
  }

  @Test
  public void testGetSchemaVersionWhenDocumentIsEmpty() {
    // given
    when(richOpenSearchClient.index()).thenReturn(indexOperations);
    when(richOpenSearchClient.doc()).thenReturn(documentOperations);
    when(indexOperations.indexExists(INDEX_NAME)).thenReturn(true);
    when(documentOperations.documentExistsWithRetries(INDEX_NAME, SCHEMA_VERSION_METADATA_ID))
        .thenReturn(true);
    when(documentOperations.getWithRetries(eq(INDEX_NAME), eq(SCHEMA_VERSION_METADATA_ID), any()))
        .thenReturn(Optional.empty());

    // when
    final String result = underTest.getSchemaVersion();

    // then
    assertThat(result).isNull();
    verify(indexOperations).indexExists(INDEX_NAME);
    verify(documentOperations).documentExistsWithRetries(INDEX_NAME, SCHEMA_VERSION_METADATA_ID);
    verify(documentOperations).getWithRetries(INDEX_NAME, SCHEMA_VERSION_METADATA_ID, Map.class);
  }

  @Test
  public void testGetSchemaVersionSuccess() {
    // given
    when(richOpenSearchClient.index()).thenReturn(indexOperations);
    when(richOpenSearchClient.doc()).thenReturn(documentOperations);
    when(indexOperations.indexExists(INDEX_NAME)).thenReturn(true);
    when(documentOperations.documentExistsWithRetries(INDEX_NAME, SCHEMA_VERSION_METADATA_ID))
        .thenReturn(true);
    final Map<String, Object> document =
        Map.of(
            MetadataIndex.ID, SCHEMA_VERSION_METADATA_ID,
            MetadataIndex.VALUE, SCHEMA_VERSION);
    when(documentOperations.getWithRetries(eq(INDEX_NAME), eq(SCHEMA_VERSION_METADATA_ID), any()))
        .thenReturn(Optional.of(document));

    // when
    final String result = underTest.getSchemaVersion();

    // then
    assertThat(result).isEqualTo(SCHEMA_VERSION);
    verify(indexOperations).indexExists(INDEX_NAME);
    verify(documentOperations).documentExistsWithRetries(INDEX_NAME, SCHEMA_VERSION_METADATA_ID);
    verify(documentOperations).getWithRetries(INDEX_NAME, SCHEMA_VERSION_METADATA_ID, Map.class);
  }

  @Test
  public void testStoreSchemaVersion() {
    // given
    when(richOpenSearchClient.doc()).thenReturn(documentOperations);

    // when
    underTest.storeSchemaVersion(SCHEMA_VERSION);

    // then
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<IndexRequest.Builder<Map<String, Object>>> requestCaptor =
        ArgumentCaptor.forClass(IndexRequest.Builder.class);
    verify(documentOperations).indexWithRetries(requestCaptor.capture());

    final IndexRequest<Map<String, Object>> capturedRequest = requestCaptor.getValue().build();
    assertThat(capturedRequest.index()).isEqualTo(INDEX_NAME);
    assertThat(capturedRequest.id()).isEqualTo(SCHEMA_VERSION_METADATA_ID);
    assertThat(capturedRequest.document())
        .containsExactlyEntriesOf(
            Map.of(
                MetadataIndex.ID, SCHEMA_VERSION_METADATA_ID,
                MetadataIndex.VALUE, SCHEMA_VERSION));
  }
}
