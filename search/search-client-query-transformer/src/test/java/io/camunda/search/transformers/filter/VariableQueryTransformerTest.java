/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.VariableFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import org.junit.jupiter.api.Test;

public final class VariableQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryOnlyByVariables() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (v) -> v.variable(new VariableValueFilter.Builder().name("foo").build()));

    // when
    final var searchRequest = transformQuery(variableFilter);

    // then
    // verify that the search request has been constructed properly
    // depending on the actual search query
    final SearchQueryOption queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            (t) -> {
              assertThat(t.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("name");
                        assertThat(term.value().stringValue()).isEqualTo("foo");
                      });
            });
  }

  @Test
  public void shouldQueryByVariableScopeKey() {
    // given
    final var variableFilter = FilterBuilders.variable((v) -> v.scopeKeys(4503599627370497L));

    // when
    final var searchRequest = transformQuery(variableFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("scopeKey");
              assertThat(t.value().longValue()).isEqualTo(4503599627370497L);
            });
  }

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var variableFilter = new VariableFilter.Builder().build();

    // then
    assertThat(variableFilter.variableFilters()).isEmpty();
    assertThat(variableFilter.scopeKeys()).isEmpty();
    assertThat(variableFilter.processInstanceKeys()).isEmpty();
    assertThat(variableFilter.orConditions()).isFalse();
    assertThat(variableFilter.onlyRuntimeVariables()).isFalse();
  }

  @Test
  public void shouldSetFilterValuesWithGtAndGte() {
    // given
    var variableFilterBuilder = new VariableFilter.Builder();

    // when
    final var variableFilterGt =
        variableFilterBuilder
            .scopeKeys(1L)
            .processInstanceKeys(2L)
            .variable(new VariableValueFilter.Builder().name("foo").gt("1000").build())
            .orConditions(true)
            .onlyRuntimeVariables(true)
            .build();

    // then
    assertThat(variableFilterGt.scopeKeys()).hasSize(1).contains(1L);
    assertThat(variableFilterGt.processInstanceKeys()).hasSize(1).contains(2L);
    assertThat(variableFilterGt.variableFilters()).hasSize(1).extracting("name").contains("foo");
    assertThat(variableFilterGt.variableFilters()).hasSize(1).extracting("gt").contains("1000");
    assertThat(variableFilterGt.orConditions()).isTrue();
    assertThat(variableFilterGt.onlyRuntimeVariables()).isTrue();

    variableFilterBuilder = new VariableFilter.Builder();

    // when
    final var variableFilterGte =
        variableFilterBuilder
            .scopeKeys(1L)
            .processInstanceKeys(2L)
            .variable(new VariableValueFilter.Builder().name("foo").gte("1000").build())
            .orConditions(true)
            .onlyRuntimeVariables(true)
            .build();

    // then
    assertThat(variableFilterGte.scopeKeys()).hasSize(1).contains(1L);
    assertThat(variableFilterGte.processInstanceKeys()).hasSize(1).contains(2L);
    assertThat(variableFilterGte.variableFilters()).hasSize(1).extracting("name").contains("foo");
    assertThat(variableFilterGte.variableFilters()).hasSize(1).extracting("gte").contains("1000");
    assertThat(variableFilterGte.orConditions()).isTrue();
    assertThat(variableFilterGte.onlyRuntimeVariables()).isTrue();
  }

  @Test
  public void shouldSetFilterValuesWithLtAndLte() {
    // given
    var variableFilterBuilder = new VariableFilter.Builder();

    // when
    final var variableFilterLt =
        variableFilterBuilder
            .scopeKeys(1L)
            .processInstanceKeys(2L)
            .variable(new VariableValueFilter.Builder().name("foo").lt("1000").build())
            .orConditions(true)
            .onlyRuntimeVariables(true)
            .build();

    // then
    assertThat(variableFilterLt.scopeKeys()).hasSize(1).contains(1L);
    assertThat(variableFilterLt.processInstanceKeys()).hasSize(1).contains(2L);
    assertThat(variableFilterLt.variableFilters()).hasSize(1).extracting("name").contains("foo");
    assertThat(variableFilterLt.variableFilters()).hasSize(1).extracting("lt").contains("1000");
    assertThat(variableFilterLt.orConditions()).isTrue();
    assertThat(variableFilterLt.onlyRuntimeVariables()).isTrue();

    variableFilterBuilder = new VariableFilter.Builder();

    // when
    final var variableFilterLte =
        variableFilterBuilder
            .scopeKeys(1L)
            .processInstanceKeys(2L)
            .variable(new VariableValueFilter.Builder().name("foo").lte("1000").build())
            .orConditions(true)
            .onlyRuntimeVariables(true)
            .build();

    // then
    assertThat(variableFilterLte.scopeKeys()).hasSize(1).contains(1L);
    assertThat(variableFilterLte.processInstanceKeys()).hasSize(1).contains(2L);
    assertThat(variableFilterLte.variableFilters()).hasSize(1).extracting("name").contains("foo");
    assertThat(variableFilterLte.variableFilters()).hasSize(1).extracting("lte").contains("1000");
    assertThat(variableFilterLte.orConditions()).isTrue();
    assertThat(variableFilterLte.onlyRuntimeVariables()).isTrue();
  }
}
