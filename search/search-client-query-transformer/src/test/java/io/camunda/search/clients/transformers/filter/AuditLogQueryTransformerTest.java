/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AuditLogQueryTransformerTest extends AbstractTransformerTest {

  @Test
  @DisplayName(
      "Should return terms query with authenticated categories when category property is authorized")
  void shouldReturnTermsQueryWithAuthenticatedCategoriesForCategoryProperty() {
    // given
    final var authorization = Authorization.of(a -> a.auditLog().read().authorizedByCategory());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.auditLog(f -> f), resourceAccessChecks);

    // then
    assertSearchTermsQuery(
        searchQuery.queryOption(),
        "category",
        AuditLogOperationCategory.ADMIN.name(),
        AuditLogOperationCategory.USER_TASKS.name());
  }

  @Test
  @DisplayName("Should handle multiple property names and return query with only valid properties")
  void shouldHandleMultiplePropertyNamesAndReturnQueryWithOnlyValidProperties() {
    // given
    final var authorization =
        Authorization.of(
            a -> a.auditLog().read().resourcePropertyNames(Set.of("category", "unknownProperty")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.auditLog(f -> f), resourceAccessChecks);

    // then
    assertSearchTermsQuery(
        searchQuery.queryOption(),
        "category",
        AuditLogOperationCategory.ADMIN.name(),
        AuditLogOperationCategory.USER_TASKS.name());
  }

  @Test
  @DisplayName("Should combine property-based authorization with filter conditions")
  void shouldCombinePropertyBasedAuthorizationWithFilterConditions() {
    // given
    final var filter = FilterBuilders.auditLog(f -> f.actorIds("user123"));
    final var authorization = Authorization.of(a -> a.auditLog().read().authorizedByCategory());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(filter, resourceAccessChecks);

    // then
    final var queryOption = searchQuery.queryOption();
    assertThat(queryOption)
        .as("Should combine filter query with property-based authorization in a bool query")
        .isInstanceOfSatisfying(
            io.camunda.search.clients.query.SearchBoolQuery.class,
            boolQuery -> {
              assertThat(boolQuery.must())
                  .as("Must clause should contain both filter and authorization conditions")
                  .hasSizeGreaterThanOrEqualTo(2)
                  .anySatisfy(
                      query ->
                          assertSearchTermsQuery(
                              query.queryOption(),
                              "category",
                              AuditLogOperationCategory.ADMIN.name(),
                              AuditLogOperationCategory.USER_TASKS.name()))
                  .anySatisfy(
                      query ->
                          assertThat(query.queryOption())
                              .as("Filter condition for actorIds should be present")
                              .isInstanceOfSatisfying(
                                  io.camunda.search.clients.query.SearchTermQuery.class,
                                  termQuery -> {
                                    assertThat(termQuery.field()).isEqualTo("actorId");
                                    assertThat(termQuery.value().value()).isEqualTo("user123");
                                  }));
            });
  }

  @Test
  @DisplayName("Should work with property-based authorization when tenant check is enabled")
  void shouldWorkWithPropertyBasedAuthorizationAndTenantCheck() {
    // given
    final var authorization = Authorization.of(a -> a.auditLog().read().authorizedByCategory());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var tenantCheck = TenantCheck.enabled(List.of("tenant1", "tenant2"));
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);

    // when
    final var searchQuery = transformQuery(FilterBuilders.auditLog(f -> f), resourceAccessChecks);

    // then
    final var queryOption = searchQuery.queryOption();
    assertThat(queryOption)
        .as("Should combine property-based authorization with tenant check in a bool query")
        .isInstanceOfSatisfying(
            io.camunda.search.clients.query.SearchBoolQuery.class,
            boolQuery -> {
              assertThat(boolQuery.must())
                  .as("Must clause should contain both authorization and tenant check conditions")
                  .hasSizeGreaterThanOrEqualTo(2)
                  .anySatisfy(
                      query ->
                          assertSearchTermsQuery(
                              query.queryOption(),
                              "category",
                              AuditLogOperationCategory.ADMIN.name(),
                              AuditLogOperationCategory.USER_TASKS.name()))
                  .anySatisfy(
                      query ->
                          assertThat(query.queryOption())
                              .as("Tenant check should be a bool query with should clause")
                              .isInstanceOfSatisfying(
                                  io.camunda.search.clients.query.SearchBoolQuery.class,
                                  tenantBoolQuery ->
                                      assertThat(tenantBoolQuery.should())
                                          .as("Should contain tenant check conditions")
                                          .hasSizeGreaterThanOrEqualTo(1)
                                          .anySatisfy(
                                              tenantQuery ->
                                                  assertSearchTermsQuery(
                                                      tenantQuery.queryOption(),
                                                      "tenantId",
                                                      "tenant1",
                                                      "tenant2"))));
            });
  }

  @Test
  @DisplayName("Should return match-none query when unknown property name is provided")
  void shouldReturnMatchNoneQueryForUnknownPropertyName() {
    // given
    final var authorization =
        Authorization.of(a -> a.auditLog().read().resourcePropertyNames(Set.of("unknownProperty")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.auditLog(f -> f), resourceAccessChecks);

    // then
    assertThat(searchQuery.queryOption())
        .as("Should return match-none query for unknown property name")
        .isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  @DisplayName(
      "Should return match-none query when resource property names is empty set for AUDIT_LOG")
  void shouldReturnMatchNoneQueryWhenResourcePropertyNamesIsEmpty() {
    // given
    final var authorization =
        Authorization.of(a -> a.auditLog().read().resourcePropertyNames(Set.of()));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.auditLog(f -> f), resourceAccessChecks);

    // then
    assertThat(searchQuery.queryOption())
        .as("Should return match-none query when resource property names is empty")
        .isInstanceOf(SearchMatchNoneQuery.class);
  }

  private static void assertSearchTermsQuery(
      final SearchQueryOption query, final String field, final String... expected) {
    assertThat(query)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            terms -> {
              assertThat(terms.field()).isEqualTo(field);
              assertThat(terms.values()).extracting(TypedValue::value).containsExactly(expected);
            });
  }
}
