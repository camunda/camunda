/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.filter.Operation.eq;
import static io.camunda.search.filter.Operation.exists;
import static io.camunda.search.filter.Operation.gte;
import static io.camunda.search.filter.Operation.like;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchExistsQuery;
import io.camunda.search.clients.query.SearchNestedQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchWildcardQuery;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.MetadataValueFilter;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import org.junit.jupiter.api.Test;

public final class ClusterVariableFilterTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryMetadataByExactKeyValue() {
    // given
    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(eq("CREDENTIAL")))
            .build();
    final var filter =
        new ClusterVariableFilter.Builder().metadataOperations(metadataFilter).build();

    // when
    final var searchQuery = transformQuery(filter);

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    assertThat(nested.path()).isEqualTo(ClusterVariableIndex.METADATA);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "kind");
    assertIsTermQuery(inner.must().get(1), ClusterVariableIndex.METADATA_VALUE, "CREDENTIAL");
  }

  @Test
  public void shouldQueryMetadataByNumericRange() {
    // given
    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("schemaVersion")
            .valueOperation(UntypedOperation.of(gte(2)))
            .build();
    final var filter =
        new ClusterVariableFilter.Builder().metadataOperations(metadataFilter).build();

    // when
    final var searchQuery = transformQuery(filter);

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "schemaVersion");
    final var rangeQuery = (SearchRangeQuery) inner.must().get(1).queryOption();
    assertThat(rangeQuery.field()).isEqualTo(ClusterVariableIndex.METADATA_VALUE_NUMBER);
    assertThat(rangeQuery.gte()).isEqualTo(2L);
  }

  @Test
  public void shouldQueryMetadataByLike() {
    // given
    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("schemaRef")
            .valueOperation(UntypedOperation.of(like("io.camunda.connector*")))
            .build();
    final var filter =
        new ClusterVariableFilter.Builder().metadataOperations(metadataFilter).build();

    // when
    final var searchQuery = transformQuery(filter);

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "schemaRef");
    final var wildcardQuery = (SearchWildcardQuery) inner.must().get(1).queryOption();
    assertThat(wildcardQuery.field()).isEqualTo(ClusterVariableIndex.METADATA_VALUE);
    assertThat(wildcardQuery.value()).isEqualTo("io.camunda.connector*");
  }

  @Test
  public void shouldQueryMetadataByExistence() {
    // given
    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(exists(true)))
            .build();
    final var filter =
        new ClusterVariableFilter.Builder().metadataOperations(metadataFilter).build();

    // when
    final var searchQuery = transformQuery(filter);

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    assertThat(nested.path()).isEqualTo(ClusterVariableIndex.METADATA);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "kind");
    final var existsBool = (SearchBoolQuery) inner.must().get(1).queryOption();
    final var existsFields =
        existsBool.should().stream()
            .map(SearchQuery::queryOption)
            .map(SearchExistsQuery.class::cast)
            .map(SearchExistsQuery::field)
            .toList();
    assertThat(existsFields)
        .containsExactlyInAnyOrder(
            ClusterVariableIndex.METADATA_VALUE, ClusterVariableIndex.METADATA_VALUE_NUMBER);
  }

  @Test
  public void shouldQueryMetadataByNonExistence() {
    // given
    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(exists(false)))
            .build();
    final var filter =
        new ClusterVariableFilter.Builder().metadataOperations(metadataFilter).build();

    // when
    final var searchQuery = transformQuery(filter);

    // then
    final var bool = (SearchBoolQuery) searchQuery.queryOption();
    assertThat(bool.mustNot()).hasSize(1);
    final var nested = (SearchNestedQuery) bool.mustNot().getFirst().queryOption();
    assertThat(nested.path()).isEqualTo(ClusterVariableIndex.METADATA);
    assertIsTermQuery(nested.query(), ClusterVariableIndex.METADATA_KEY, "kind");
  }

  @Test
  public void shouldRejectNotExistsCombinedWithOtherOperations() {
    // given
    final var metadataFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(exists(false)))
            .valueOperation(UntypedOperation.of(eq("CREDENTIAL")))
            .build();
    final var filter =
        new ClusterVariableFilter.Builder().metadataOperations(metadataFilter).build();

    // when / then
    assertThatThrownBy(() -> transformQuery(filter)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldAndMultipleMetadataFilters() {
    // given
    final var kindFilter =
        new MetadataValueFilter.Builder()
            .key("kind")
            .valueOperation(UntypedOperation.of(eq("CREDENTIAL")))
            .build();
    final var schemaVersionFilter =
        new MetadataValueFilter.Builder()
            .key("schemaVersion")
            .valueOperation(UntypedOperation.of(gte(2)))
            .build();
    final var filter =
        new ClusterVariableFilter.Builder()
            .metadataOperations(kindFilter, schemaVersionFilter)
            .build();

    // when
    final var searchQuery = transformQuery(filter);

    // then
    final var bool = (SearchBoolQuery) searchQuery.queryOption();
    final var nestedQueries =
        bool.must().stream().filter(q -> q.queryOption() instanceof SearchNestedQuery).toList();
    assertThat(nestedQueries).hasSize(2);
  }

  private SearchNestedQuery extractSingleNestedQuery(final SearchQuery searchQuery) {
    if (searchQuery.queryOption() instanceof SearchNestedQuery nestedQuery) {
      return nestedQuery;
    }
    final var bool = (SearchBoolQuery) searchQuery.queryOption();
    return bool.must().stream()
        .map(SearchQuery::queryOption)
        .filter(SearchNestedQuery.class::isInstance)
        .map(SearchNestedQuery.class::cast)
        .findFirst()
        .orElseThrow();
  }

  private void assertIsTermQuery(
      final SearchQuery searchQuery, final String field, final String value) {
    final var termQuery = (SearchTermQuery) searchQuery.queryOption();
    assertThat(termQuery.field()).isEqualTo(field);
    assertThat(termQuery.value().stringValue()).isEqualTo(value);
  }
}
