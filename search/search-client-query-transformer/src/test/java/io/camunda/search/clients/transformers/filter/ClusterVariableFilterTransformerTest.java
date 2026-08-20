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
import static io.camunda.search.filter.Operation.gt;
import static io.camunda.search.filter.Operation.gte;
import static io.camunda.search.filter.Operation.in;
import static io.camunda.search.filter.Operation.like;
import static io.camunda.search.filter.Operation.lt;
import static io.camunda.search.filter.Operation.lte;
import static io.camunda.search.filter.Operation.neq;
import static io.camunda.search.filter.Operation.notIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchExistsQuery;
import io.camunda.search.clients.query.SearchNestedQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.query.SearchWildcardQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MetadataValueFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import org.junit.jupiter.api.Test;

public final class ClusterVariableFilterTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryMetadataByExactKeyValue() {
    // when
    final var searchQuery = transformMetadataQuery("kind", eq("CREDENTIAL"));

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
    // when
    final var searchQuery = transformMetadataQuery("schemaVersion", gte(2));

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "schemaVersion");
    final var rangeQuery = (SearchRangeQuery) inner.must().get(1).queryOption();
    assertThat(rangeQuery.field()).isEqualTo(ClusterVariableIndex.METADATA_VALUE_NUMBER);
    assertThat(rangeQuery.gte()).isEqualTo(2.0);
  }

  @Test
  public void shouldQueryMetadataByNumericDoubleRange() {
    // when
    final var searchQuery = transformMetadataQuery("schemaVersion", gte(2.5));

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "schemaVersion");
    final var rangeQuery = (SearchRangeQuery) inner.must().get(1).queryOption();
    assertThat(rangeQuery.field()).isEqualTo(ClusterVariableIndex.METADATA_VALUE_NUMBER);
    assertThat(rangeQuery.gte()).isEqualTo(2.5);
  }

  @Test
  public void shouldQueryMetadataByLike() {
    // when
    final var searchQuery = transformMetadataQuery("schemaRef", like("io.camunda.connector*"));

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
    // when
    final var searchQuery = transformMetadataQuery("kind", exists(true));

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    assertThat(nested.path()).isEqualTo(ClusterVariableIndex.METADATA);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "kind");
    // exists query should only check if the value exists (not the value number)
    final var existsQuery = (SearchExistsQuery) inner.must().get(1).queryOption();
    assertThat(existsQuery.field()).isEqualTo(ClusterVariableIndex.METADATA_VALUE);
  }

  @Test
  public void shouldQueryMetadataByNonExistence() {
    // when
    final var searchQuery = transformMetadataQuery("kind", exists(false));

    // then
    final var bool = (SearchBoolQuery) searchQuery.queryOption();
    assertThat(bool.mustNot()).hasSize(1);
    final var nested = (SearchNestedQuery) bool.mustNot().getFirst().queryOption();
    assertThat(nested.path()).isEqualTo(ClusterVariableIndex.METADATA);
    assertIsTermQuery(nested.query(), ClusterVariableIndex.METADATA_KEY, "kind");
  }

  @Test
  public void shouldRejectNotExistsCombinedWithOtherOperations() {
    // when / then
    assertThatThrownBy(() -> transformMetadataQuery("kind", exists(false), eq("CREDENTIAL")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldQueryMetadataByKeyOnly() {
    // when
    final var searchQuery = transformMetadataQuery("kind");

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    assertThat(nested.path()).isEqualTo(ClusterVariableIndex.METADATA);
    assertIsTermQuery(nested.query(), ClusterVariableIndex.METADATA_KEY, "kind");
  }

  @Test
  public void shouldQueryMetadataByNotEquals() {
    // when
    final var searchQuery = transformMetadataQuery("kind", neq("CREDENTIAL"));

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "kind");
    final var notBool = (SearchBoolQuery) inner.must().get(1).queryOption();
    assertIsTermQuery(
        notBool.mustNot().getFirst(), ClusterVariableIndex.METADATA_VALUE, "CREDENTIAL");
  }

  @Test
  public void shouldQueryMetadataByNumericLessThan() {
    // when
    final var searchQuery = transformMetadataQuery("schemaVersion", lt(5));

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "schemaVersion");
    final var rangeQuery = (SearchRangeQuery) inner.must().get(1).queryOption();
    assertThat(rangeQuery.field()).isEqualTo(ClusterVariableIndex.METADATA_VALUE_NUMBER);
    assertThat(rangeQuery.lt()).isEqualTo(5.0);
  }

  @Test
  public void shouldQueryMetadataByNumericLessThanEquals() {
    // when
    final var searchQuery = transformMetadataQuery("schemaVersion", lte(5));

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "schemaVersion");
    final var rangeQuery = (SearchRangeQuery) inner.must().get(1).queryOption();
    assertThat(rangeQuery.field()).isEqualTo(ClusterVariableIndex.METADATA_VALUE_NUMBER);
    assertThat(rangeQuery.lte()).isEqualTo(5.0);
  }

  @Test
  public void shouldQueryMetadataByNumericGreaterThan() {
    // when
    final var searchQuery = transformMetadataQuery("schemaVersion", gt(1));

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "schemaVersion");
    final var rangeQuery = (SearchRangeQuery) inner.must().get(1).queryOption();
    assertThat(rangeQuery.field()).isEqualTo(ClusterVariableIndex.METADATA_VALUE_NUMBER);
    assertThat(rangeQuery.gt()).isEqualTo(1.0);
  }

  @Test
  public void shouldQueryMetadataByIn() {
    // when
    final var searchQuery = transformMetadataQuery("kind", in("CREDENTIAL", "SECRET"));

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "kind");
    final var termsQuery = (SearchTermsQuery) inner.must().get(1).queryOption();
    assertThat(termsQuery.field()).isEqualTo(ClusterVariableIndex.METADATA_VALUE);
  }

  @Test
  public void shouldQueryMetadataByNotIn() {
    // when
    final var searchQuery = transformMetadataQuery("kind", notIn("CREDENTIAL", "SECRET"));

    // then
    final var nested = extractSingleNestedQuery(searchQuery);
    final var inner = (SearchBoolQuery) nested.query().queryOption();
    assertThat(inner.must()).hasSize(2);
    assertIsTermQuery(inner.must().get(0), ClusterVariableIndex.METADATA_KEY, "kind");
    final var notBool = (SearchBoolQuery) inner.must().get(1).queryOption();
    final var termsQuery = (SearchTermsQuery) notBool.mustNot().getFirst().queryOption();
    assertThat(termsQuery.field()).isEqualTo(ClusterVariableIndex.METADATA_VALUE);
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

  @Test
  void shouldTransformKindFilter() {
    // given
    final var filter = FilterBuilders.clusterVariable().kinds("JSON").build();

    // when
    final var query = transformQuery(filter);

    // then
    // single-value kinds() maps to EQ → SearchTermQuery, returned directly (no bool wrapper)
    assertThat(query.queryOption()).isInstanceOf(SearchTermQuery.class);
    final var termQuery = (SearchTermQuery) query.queryOption();
    assertThat(termQuery.field()).isEqualTo(ClusterVariableIndex.KIND);
    assertThat(termQuery.value().stringValue()).isEqualTo("JSON");
  }

  @Test
  void shouldTransformMultipleKindFilter() {
    // given
    final var filter = FilterBuilders.clusterVariable().kinds("JSON", "SECRET_REFERENCE").build();

    // when
    final var query = transformQuery(filter);

    // then
    // multiple values → IN → SearchTermsQuery, returned directly (no bool wrapper)
    assertThat(query.queryOption()).isInstanceOf(SearchTermsQuery.class);
    final var terms = (SearchTermsQuery) query.queryOption();
    assertThat(terms.field()).isEqualTo(ClusterVariableIndex.KIND);
    assertThat(terms.values().stream().map(TypedValue::stringValue).toList())
        .containsExactly("JSON", "SECRET_REFERENCE");
  }

  private SearchQuery transformMetadataQuery(final String key, final Operation<?>... operations) {
    final var builder = new MetadataValueFilter.Builder().key(key);
    for (final var op : operations) {
      builder.valueOperation(UntypedOperation.of(op));
    }
    final var filter =
        new ClusterVariableFilter.Builder().metadataOperations(builder.build()).build();
    return transformQuery(filter);
  }

  private SearchNestedQuery extractSingleNestedQuery(final SearchQuery searchQuery) {
    if (searchQuery.queryOption() instanceof final SearchNestedQuery nestedQuery) {
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
