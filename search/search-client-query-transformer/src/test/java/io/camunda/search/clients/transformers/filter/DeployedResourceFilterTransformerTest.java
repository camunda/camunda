/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchExistsQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import org.junit.jupiter.api.Test;

class DeployedResourceFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldTreatEmptyVersionTagAsMissing() {
    // given
    // A missing version tag has two possible representations in the search index:
    // the field is absent, or the field exists but contains an empty string.
    final var filter =
        FilterBuilders.deployedResource(
            builder -> builder.versionTagOperations(Operation.exists(false)));

    // when
    final var query = transformQuery(filter);

    // then
    // The expected query is: NOT EXISTS versionTag OR versionTag = "".
    // queryOption() unwraps the generic SearchQuery so its concrete query type can be verified.
    assertThat(query.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            anyMissingVersionTag -> {
              // A bool query's "should" clauses represent the two OR alternatives.
              assertThat(anyMissingVersionTag.should()).hasSize(2);

              // First alternative: the versionTag field does not exist.
              assertThat(anyMissingVersionTag.should().getFirst().queryOption())
                  .isInstanceOfSatisfying(
                      SearchBoolQuery.class,
                      missingField ->
                          assertThat(missingField.mustNot())
                              .singleElement()
                              .extracting(nested -> nested.queryOption())
                              .isInstanceOfSatisfying(
                                  SearchExistsQuery.class,
                                  exists -> assertThat(exists.field()).isEqualTo("versionTag")));

              // Second alternative: the versionTag field exists with an empty value.
              assertThat(anyMissingVersionTag.should().getLast().queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      emptyValue -> {
                        assertThat(emptyValue.field()).isEqualTo("versionTag");
                        assertThat(emptyValue.value().stringValue()).isEmpty();
                      });
            });
  }

  @Test
  void shouldNotTreatEmptyVersionTagAsExisting() {
    // given
    // A version tag should count as existing only when it contains a non-empty value.
    final var filter =
        FilterBuilders.deployedResource(
            builder -> builder.versionTagOperations(Operation.exists(true)));

    // when
    final var query = transformQuery(filter);

    // then
    // The expected query is: EXISTS versionTag AND NOT versionTag = "".
    assertThat(query.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            existingNonEmptyVersionTag -> {
              // A bool query's "must" clauses represent the two required AND conditions.
              assertThat(existingNonEmptyVersionTag.must()).hasSize(2);

              // First condition: the versionTag field exists.
              assertThat(existingNonEmptyVersionTag.must().getFirst().queryOption())
                  .isInstanceOfSatisfying(
                      SearchExistsQuery.class,
                      exists -> assertThat(exists.field()).isEqualTo("versionTag"));

              // Second condition: exclude documents whose versionTag is empty.
              assertThat(existingNonEmptyVersionTag.must().getLast().queryOption())
                  .isInstanceOfSatisfying(
                      SearchBoolQuery.class,
                      nonEmptyValue ->
                          assertThat(nonEmptyValue.mustNot())
                              .singleElement()
                              .extracting(nested -> nested.queryOption())
                              .isInstanceOfSatisfying(
                                  SearchTermQuery.class,
                                  emptyValue -> {
                                    assertThat(emptyValue.field()).isEqualTo("versionTag");
                                    assertThat(emptyValue.value().stringValue()).isEmpty();
                                  }));
            });
  }
}
