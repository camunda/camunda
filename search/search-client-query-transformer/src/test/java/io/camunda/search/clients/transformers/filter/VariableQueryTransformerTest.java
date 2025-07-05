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
import io.camunda.search.clients.query.SearchMatchAllQuery;
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.AuthorizationBasedResourceAccessFilter;
import io.camunda.security.resource.ResourceAccessFilter;
import io.camunda.security.resource.TenantBasedResourceAccessFilter;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import org.junit.jupiter.api.Test;

public class VariableQueryTransformerTest extends AbstractTransformerTest {
  @Test
  public void shouldQueryByVariableKey() {
    // given
    final var filter = FilterBuilders.variable((f) -> f.variableKeys(12345L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class, // Now expecting SearchTermQuery directly
            (term) -> {
              assertThat(term.field()).isEqualTo("key");
              assertThat(term.value().longValue()).isEqualTo(12345L);
            });
  }

  @Test
  public void shouldQueryByScopeKey() {
    // given
    final var filter = FilterBuilders.variable((f) -> f.scopeKeys(12345L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class, // Now expecting SearchTermQuery directly
            (term) -> {
              assertThat(term.field()).isEqualTo("scopeKey");
              assertThat(term.value().longValue()).isEqualTo(12345L);
            });
  }

  @Test
  public void shouldQueryByProcessInstanceKey() {
    // given
    final var filter = FilterBuilders.variable((f) -> f.processInstanceKeys(12345L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class, // Now expecting SearchTermQuery directly
            (term) -> {
              assertThat(term.field()).isEqualTo("processInstanceKey");
              assertThat(term.value().longValue()).isEqualTo(12345L);
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var filter = FilterBuilders.variable((f) -> f.tenantIds("tenantId"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class, // Now expecting SearchTermQuery directly
            (term) -> {
              assertThat(term.field()).isEqualTo("tenantId");
              assertThat(term.value().stringValue()).isEqualTo("tenantId");
            });
  }

  @Test
  public void shouldQueryByVariableNameAndValue() {
    // given
    final var filter = FilterBuilders.variable((f) -> f.names("test").values("testValue"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();

    // Ensure the outer query is a SearchBoolQuery
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            outerBoolQuery -> {
              assertThat(outerBoolQuery.must()).isNotEmpty();

              final SearchQuery nameMustQuery = outerBoolQuery.must().get(0);
              assertThat(nameMustQuery.queryOption()).isInstanceOf(SearchTermQuery.class);

              final SearchQuery valueMustQuery = outerBoolQuery.must().get(1);
              assertThat(valueMustQuery.queryOption()).isInstanceOf(SearchTermQuery.class);

              final SearchTermQuery innerNameTermQuery =
                  (SearchTermQuery) nameMustQuery.queryOption();
              final SearchTermQuery innerValueTermQuery =
                  (SearchTermQuery) valueMustQuery.queryOption();

              // Ensure name query is correct
              assertThat(innerNameTermQuery)
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("name");
                        assertThat(term.value().stringValue()).isEqualTo("test");
                      });

              // Ensure value query is correct
              assertThat(innerValueTermQuery)
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("value");
                        assertThat(term.value().stringValue()).isEqualTo("testValue");
                      });
            });
  }

  @Test
  public void shouldApplyAuthorizationFilterWithResourceIds() {
    // given
    final var filter = FilterBuilders.variable(b -> b);
    final var expectedAuthorization =
        Authorization.of(
            a ->
                a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                    .permissionType(PermissionType.READ)
                    .resourceIds(List.of("123")));
    final var authorizationFilter =
        AuthorizationBasedResourceAccessFilter.requiredAuthorizationCheck(expectedAuthorization);

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.authorizationFilter(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("bpmnProcessId");
              assertThat(t.value().value()).isEqualTo("123");
            });
  }

  @Test
  public void shouldApplyAuthorizationFilterWithGranted() {
    // given
    final var filter = FilterBuilders.variable(b -> b);
    final var authorizationFilter = AuthorizationBasedResourceAccessFilter.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.authorizationFilter(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchAllQuery.class);
  }

  @Test
  public void shouldApplyAuthorizationFilterWithForbidden() {
    // given
    final var filter = FilterBuilders.variable(b -> b);
    final var authorizationFilter = AuthorizationBasedResourceAccessFilter.unsuccessful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.authorizationFilter(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldApplyTenantFilterWithGranted() {
    // given
    final var filter = FilterBuilders.variable(b -> b);
    final var tenantFilter = TenantBasedResourceAccessFilter.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.tenantFilter(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchAllQuery.class);
  }

  @Test
  public void shouldApplyTenantFilterWithForbidden() {
    // given
    final var filter = FilterBuilders.variable(b -> b);
    final var tenantFilter = TenantBasedResourceAccessFilter.unsuccessful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.tenantFilter(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldIgnoreTenantFilterWithTenantIds() {
    // given
    final var filter = FilterBuilders.variable(b -> b);
    final var tenantFilter = TenantBasedResourceAccessFilter.tenantCheckRequired(List.of("bar"));

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.tenantFilter(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().value()).isEqualTo("bar");
            });
  }

  @Test
  public void shouldApplyAllFilters() {
    // given
    final var filter = FilterBuilders.variable(b -> b.names("foo"));
    final var authorizationFilter = AuthorizationBasedResourceAccessFilter.successful();
    final var tenantFilter = TenantBasedResourceAccessFilter.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter,
            ResourceAccessFilter.of(
                b -> b.authorizationFilter(authorizationFilter).tenantFilter(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    final var must = ((SearchBoolQuery) queryVariant).must();
    assertThat(must).hasSize(3);
  }
}
