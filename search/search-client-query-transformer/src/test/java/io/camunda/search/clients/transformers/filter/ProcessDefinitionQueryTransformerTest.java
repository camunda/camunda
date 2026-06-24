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
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public final class ProcessDefinitionQueryTransformerTest extends AbstractTransformerTest {

  /**
   * Extracts the non-isDeleted criterion from a must-combined query. When the filter has exactly
   * one user-supplied criterion, the top-level query is a bool must that combines that criterion
   * with the mandatory isDeleted=false exclusion.
   */
  private static Optional<SearchQuery> findCriterion(final SearchQuery query, final String field) {
    if (query.queryOption() instanceof final SearchBoolQuery boolQuery) {
      return boolQuery.must().stream()
          .filter(
              q ->
                  !(q.queryOption() instanceof SearchBoolQuery inner && !inner.mustNot().isEmpty()))
          .filter(q -> matchesField(q, field))
          .findFirst();
    }
    return Optional.empty();
  }

  private static boolean matchesField(final SearchQuery q, final String field) {
    return switch (q.queryOption()) {
      case final SearchTermQuery t -> field.equals(t.field());
      case final SearchTermsQuery t -> field.equals(t.field());
      default -> false;
    };
  }

  @Test
  public void shouldQueryByProcessDefinitionKey() {
    final var filter = FilterBuilders.processDefinition(f -> f.processDefinitionKeys(1L));

    // when
    final var searchRequest = transformQuery(filter);

    // then — single criterion + isDeleted exclusion → outer bool/must
    final var criterion = findCriterion(searchRequest, "key");
    assertThat(criterion).isPresent();
    assertThat(criterion.get().queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(1L);
            });
  }

  @Test
  public void shouldQueryByName() {
    final var filter = FilterBuilders.processDefinition(f -> f.names("Order process"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var criterion = findCriterion(searchRequest, "name");
    assertThat(criterion).isPresent();
    assertThat(criterion.get().queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("name");
              assertThat(t.value().stringValue()).isEqualTo("Order process");
            });
  }

  @Test
  public void shouldQueryByProcessDefinitionId() {
    final var filter =
        FilterBuilders.processDefinition(f -> f.processDefinitionIds("complexProcess"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var criterion = findCriterion(searchRequest, "bpmnProcessId");
    assertThat(criterion).isPresent();
    assertThat(criterion.get().queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("bpmnProcessId");
              assertThat(t.value().stringValue()).isEqualTo("complexProcess");
            });
  }

  @Test
  public void shouldQueryByVersion() {
    final var filter = FilterBuilders.processDefinition(f -> f.versions(5));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var criterion = findCriterion(searchRequest, "version");
    assertThat(criterion).isPresent();
    assertThat(criterion.get().queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("version");
              assertThat(t.value().intValue()).isEqualTo(5);
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldQueryByHasStartForm(final boolean hasStartForm) {
    final var filter = FilterBuilders.processDefinition(f -> f.hasStartForm(hasStartForm));

    // when
    final var searchRequest = transformQuery(filter);

    // then — outer must combines the hasStartForm bool with the isDeleted exclusion bool
    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            outerBool -> {
              // find the inner bool that encodes the hasStartForm check
              final var startFormClause =
                  outerBool.must().stream()
                      .filter(
                          q ->
                              q.queryOption() instanceof SearchBoolQuery inner
                                  && inner.mustNot().stream()
                                      .noneMatch(
                                          mn ->
                                              mn.queryOption() instanceof SearchTermQuery t
                                                  && ProcessIndex.IS_DELETED.equals(t.field())))
                      .findFirst();
              assertThat(startFormClause).isPresent();
              assertThat(startFormClause.get().queryOption())
                  .isInstanceOfSatisfying(
                      SearchBoolQuery.class,
                      boolQuery -> {
                        final var boolQueryMustOrNot =
                            hasStartForm ? boolQuery.must() : boolQuery.mustNot();
                        assertThat(boolQueryMustOrNot).hasSize(1);
                        assertThat(boolQueryMustOrNot.get(0).queryOption())
                            .isInstanceOf(SearchExistsQuery.class);
                        assertThat(
                                ((SearchExistsQuery) boolQueryMustOrNot.get(0).queryOption())
                                    .field())
                            .isEqualTo("formId");
                      });
            });
  }

  @Test
  public void shouldQueryByVersionTag() {
    final var filter = FilterBuilders.processDefinition(f -> f.versionTags("alpha"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var criterion = findCriterion(searchRequest, "versionTag");
    assertThat(criterion).isPresent();
    assertThat(criterion.get().queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("versionTag");
              assertThat(t.value().stringValue()).isEqualTo("alpha");
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    final var filter = FilterBuilders.processDefinition(f -> f.tenantIds("<default>"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var criterion = findCriterion(searchRequest, "tenantId");
    assertThat(criterion).isPresent();
    assertThat(criterion.get().queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("<default>");
            });
  }

  @Test
  public void shouldQueryByResourceName() {
    final var filter =
        FilterBuilders.processDefinition(f -> f.resourceNames("usertest/single-task.bpmn"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var criterion = findCriterion(searchRequest, "resourceName");
    assertThat(criterion).isPresent();
    assertThat(criterion.get().queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("resourceName");
              assertThat(t.value().stringValue()).isEqualTo("usertest/single-task.bpmn");
            });
  }

  @Test
  public void shouldApplyAuthorizationCheck() {
    // given
    final var authorization =
        RequiredAuthorization.of(
            a -> a.processDefinition().readProcessDefinition().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.processDefinition(b -> b), resourceAccessChecks);

    // then — combined: filterQuery (isDeleted exclusion) + authQuery
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            boolQuery -> {
              final var authClause =
                  boolQuery.must().stream()
                      .filter(
                          q ->
                              q.queryOption() instanceof SearchTermsQuery t
                                  && ProcessIndex.BPMN_PROCESS_ID.equals(t.field()))
                      .findFirst();
              assertThat(authClause).isPresent();
              final var termsQuery = (SearchTermsQuery) authClause.get().queryOption();
              assertThat(termsQuery.values()).hasSize(2);
              assertThat(termsQuery.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("1", "2");
            });
  }

  @Test
  public void shouldReturnNonMatchWhenNoResourceIdsProvided() {
    // given
    final var authorization =
        RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.processDefinition(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldIgnoreAuthorizationCheckWhenDisabled() {
    // given
    final var authorizationCheck = AuthorizationCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.processDefinition(b -> b), resourceAccessChecks);

    // then — only the isDeleted exclusion remains (no other filters or checks)
    assertThat(searchQuery).isNotNull();
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            boolQuery -> {
              assertThat(boolQuery.mustNot()).hasSize(1);
              assertThat(boolQuery.mustNot().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      t -> {
                        assertThat(t.field()).isEqualTo(ProcessIndex.IS_DELETED);
                        assertThat(t.value().booleanValue()).isTrue();
                      });
            });
  }

  @Test
  public void shouldApplyTenantCheck() {
    // given
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.processDefinition(b -> b), resourceAccessChecks);

    // then — combined: filterQuery (isDeleted exclusion) + tenantQuery
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            boolQuery -> {
              final var tenantClause =
                  boolQuery.must().stream()
                      .filter(
                          q ->
                              q.queryOption() instanceof SearchTermsQuery t
                                  && ProcessIndex.TENANT_ID.equals(t.field()))
                      .findFirst();
              assertThat(tenantClause).isPresent();
              final var termsQuery = (SearchTermsQuery) tenantClause.get().queryOption();
              assertThat(termsQuery.values()).hasSize(2);
              assertThat(termsQuery.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("a", "b");
            });
  }

  @Test
  public void shouldIgnoreTenantCheckWhenDisabled() {
    // given
    final var tenantCheck = TenantCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.processDefinition(b -> b), resourceAccessChecks);

    // then — only the isDeleted exclusion remains
    assertThat(searchQuery).isNotNull();
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            boolQuery -> {
              assertThat(boolQuery.mustNot()).hasSize(1);
              assertThat(boolQuery.mustNot().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      t -> assertThat(t.field()).isEqualTo(ProcessIndex.IS_DELETED));
            });
  }

  @Test
  public void shouldApplyFilterAndChecks() {
    // given
    final var authorization =
        RequiredAuthorization.of(
            a -> a.processDefinition().readProcessDefinition().resourceIds(List.of("1", "2")));

    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.processDefinition(b -> b.processDefinitionIds("abc")),
            resourceAccessChecks);

    // then — 3 top-level must clauses: filter+isDeleted, auth check, tenant check
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(SearchBoolQuery.class, t -> assertThat(t.must()).hasSize(3));
  }
}
