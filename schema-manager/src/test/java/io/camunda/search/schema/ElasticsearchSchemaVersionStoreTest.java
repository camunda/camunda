/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.zeebe.util.migration.CurrentSchemaVersion.Kind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElasticsearchSchemaVersionStoreTest {

  @Test
  void shouldReportAvailableForExistingSchemaVersion() {
    // given - metadata index exists with a stored schema version
    final var store = store(fakeClient("8.10.0"), "8.10.0");

    // when
    final var currentSchemaVersion = store.getCurrentSchemaVersion();

    // then
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.AVAILABLE);
    assertThat(currentSchemaVersion.schemaVersion()).contains("8.10.0");
    assertThat(currentSchemaVersion.stableApplicationVersion()).contains("8.10.0");
  }

  @Test
  void shouldNormalizeSnapshotSuffixOnStoredSchemaVersion() {
    // given - SchemaManager stores VersionUtil.getVersion() as-is (unlike RdbmsSchemaVersionStore,
    // which normalizes before every write), so a non-release build's stored schema version may
    // still carry a "-SNAPSHOT" suffix
    final var store = store(fakeClient("8.10.0-SNAPSHOT"), "8.10.0-SNAPSHOT");

    // when
    final var currentSchemaVersion = store.getCurrentSchemaVersion();

    // then - both sides are normalized before comparison, so a matching non-release build still
    // reports AVAILABLE with a stable (unsuffixed) schema version
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.AVAILABLE);
    assertThat(currentSchemaVersion.schemaVersion()).contains("8.10.0");
    assertThat(currentSchemaVersion.stableApplicationVersion()).contains("8.10.0");
  }

  @Test
  void shouldReportFreshDatabaseWhenMetadataIndexDoesNotExist() {
    // given - metadata index does not exist yet
    final var searchEngineClient = mock(SearchEngineClient.class);
    when(searchEngineClient.indexExists(anyString())).thenReturn(false);
    final var store = store(searchEngineClient, "8.10.0");

    // when
    final var currentSchemaVersion = store.getCurrentSchemaVersion();

    // then
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.FRESH_DATABASE);
  }

  @Test
  void shouldReportFreshDatabaseWhenNoVersionDocumentStored() {
    // given - metadata index exists, but no schema-version document was ever written
    final var searchEngineClient = mock(SearchEngineClient.class);
    when(searchEngineClient.indexExists(anyString())).thenReturn(true);
    when(searchEngineClient.getDocument(anyString(), anyString())).thenReturn(null);
    final var store = store(searchEngineClient, "8.10.0");

    // when
    final var currentSchemaVersion = store.getCurrentSchemaVersion();

    // then
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.FRESH_DATABASE);
  }

  @Test
  void shouldReportReadFailureWhenApplicationVersionIsMissing() {
    // given - applicationVersion not configured
    final var store = store(fakeClient("8.10.0"), null);

    // when
    final var currentSchemaVersion = store.getCurrentSchemaVersion();

    // then
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.READ_FAILURE);
    assertThat(currentSchemaVersion.detail()).isPresent();
  }

  @Test
  void shouldReportReadFailureWhenApplicationVersionIsUnparseable() {
    // given - applicationVersion is not a valid semantic version
    final var store = store(fakeClient("8.10.0"), "development");

    // when
    final var currentSchemaVersion = store.getCurrentSchemaVersion();

    // then
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.READ_FAILURE);
    assertThat(currentSchemaVersion.detail())
        .hasValueSatisfying(d -> assertThat(d).contains("development"));
  }

  @Test
  void shouldReportReadFailureWhenSearchEngineClientThrows() {
    // given - the search engine call itself fails (e.g. connection refused)
    final var searchEngineClient = mock(SearchEngineClient.class);
    when(searchEngineClient.indexExists(anyString()))
        .thenThrow(new RuntimeException("connection refused"));
    final var store = store(searchEngineClient, "8.10.0");

    // when
    final var currentSchemaVersion = store.getCurrentSchemaVersion();

    // then
    assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.READ_FAILURE);
    assertThat(currentSchemaVersion.detail())
        .hasValueSatisfying(d -> assertThat(d).contains("connection refused"));
  }

  private static ElasticsearchSchemaVersionStore store(
      final SearchEngineClient searchEngineClient, final String applicationVersion) {
    return new ElasticsearchSchemaVersionStore(searchEngineClient, "", true, applicationVersion);
  }

  /** A {@link SearchEngineClient} stub reporting {@code schemaVersion} as already stored. */
  private static SearchEngineClient fakeClient(final String schemaVersion) {
    final var searchEngineClient = mock(SearchEngineClient.class);
    when(searchEngineClient.indexExists(anyString())).thenReturn(true);
    when(searchEngineClient.getDocument(anyString(), anyString()))
        .thenReturn(Map.of(MetadataIndex.VALUE, schemaVersion));
    return searchEngineClient;
  }
}
