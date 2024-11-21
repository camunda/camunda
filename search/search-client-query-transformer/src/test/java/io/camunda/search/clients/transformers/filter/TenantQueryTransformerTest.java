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
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import org.junit.jupiter.api.Test;

public class TenantQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByTenantKey() {
    // given
    final var filter = FilterBuilders.tenant((f) -> f.key(12345L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (termQuery) -> {
              assertThat(termQuery.field()).isEqualTo("key");
              assertThat(termQuery.value().longValue()).isEqualTo(12345L);
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var filter = FilterBuilders.tenant((f) -> f.tenantId("tenant1"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (termQuery) -> {
              assertThat(termQuery.field()).isEqualTo("tenantId");
              assertThat(termQuery.value().stringValue()).isEqualTo("tenant1");
            });
  }

  @Test
  public void shouldQueryByTenantName() {
    // given
    final var filter = FilterBuilders.tenant((f) -> f.name("TestTenant"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (termQuery) -> {
              assertThat(termQuery.field()).isEqualTo("name");
              assertThat(termQuery.value().stringValue()).isEqualTo("TestTenant");
            });
  }

  @Test
  public void shouldQueryByMultipleTenantFields() {
    // given
    final var filter =
        FilterBuilders.tenant((f) -> f.key(12345L).tenantId("tenant1").name("TestTenant"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            (boolQuery) -> {
              assertThat(boolQuery.must()).hasSize(3);

              // Verify "key" filter
              assertThat(boolQuery.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (termQuery) -> {
                        assertThat(termQuery.field()).isEqualTo("key");
                        assertThat(termQuery.value().longValue()).isEqualTo(12345L);
                      });

              // Verify "tenantId" filter
              assertThat(boolQuery.must().get(1).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (termQuery) -> {
                        assertThat(termQuery.field()).isEqualTo("tenantId");
                        assertThat(termQuery.value().stringValue()).isEqualTo("tenant1");
                      });

              // Verify "name" filter
              assertThat(boolQuery.must().get(2).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (termQuery) -> {
                        assertThat(termQuery.field()).isEqualTo("name");
                        assertThat(termQuery.value().stringValue()).isEqualTo("TestTenant");
                      });
            });
  }
}
