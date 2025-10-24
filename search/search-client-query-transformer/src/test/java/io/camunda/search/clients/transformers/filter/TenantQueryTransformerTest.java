/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.zeebe.protocol.record.value.EntityType.USER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.descriptors.index.TenantIndex;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class TenantQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByTenantKey() {
    // given
    final var filter = FilterBuilders.tenant((f) -> f.key(12345L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest)
        .isEqualTo(
            SearchQuery.of(
                q ->
                    q.bool(
                        b ->
                            b.must(
                                List.of(
                                    SearchQuery.of(q1 -> q.term(t -> t.field("key").value(12345L))),
                                    SearchQuery.of(
                                        q1 -> q.term(t -> t.field("join").value("tenant"))))))));
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var filter = FilterBuilders.tenant((f) -> f.tenantId("tenant1"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest)
        .isEqualTo(
            SearchQuery.of(
                q ->
                    q.bool(
                        b ->
                            b.must(
                                List.of(
                                    SearchQuery.of(
                                        q1 -> q.term(t -> t.field("tenantId").value("tenant1"))),
                                    SearchQuery.of(
                                        q1 -> q.term(t -> t.field("join").value("tenant"))))))));
  }

  @Test
  public void shouldQueryByTenantName() {
    // given
    final var filter = FilterBuilders.tenant((f) -> f.name("TestTenant"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest)
        .isEqualTo(
            SearchQuery.of(
                q ->
                    q.bool(
                        b ->
                            b.must(
                                List.of(
                                    SearchQuery.of(
                                        q1 -> q.term(t -> t.field("name").value("TestTenant"))),
                                    SearchQuery.of(
                                        q1 -> q.term(t -> t.field("join").value("tenant"))))))));
  }

  @Test
  void shouldQueryTenantsByMemberId() {
    // given
    final var filter =
        FilterBuilders.tenant((f) -> f.memberIds(Set.of("test-member-id")).childMemberType(USER));

    // when
    final var query = (SearchBoolQuery) transformQuery(filter).queryOption();

    // then
    final var memberTypeQuery =
        SearchQuery.of(q1 -> q1.term(t -> t.field("memberType").value(USER.name())));
    final var expectedChildMemberTypeQuery =
        SearchQuery.of(q -> q.hasChild(hc -> hc.type("member").query(memberTypeQuery)));
    final var memberIdQuery =
        SearchQuery.of(q1 -> q1.term(t -> t.field("memberId").value("test-member-id")));
    final var expectedChildMemberIdQuery =
        SearchQuery.of(q -> q.hasChild(hc -> hc.type("member").query(memberIdQuery)));
    final var joinQuery = SearchQuery.of(q1 -> q1.term(t -> t.field("join").value("tenant")));

    assertThat(query.filter()).isEmpty();
    assertThat(query.should()).isEmpty();
    assertThat(query.must())
        .containsExactlyInAnyOrder(
            expectedChildMemberTypeQuery, expectedChildMemberIdQuery, joinQuery);
  }

  @Test
  public void shouldQueryByMultipleTenantFields() {
    // given
    final var filter =
        FilterBuilders.tenant((f) -> f.key(12345L).tenantId("tenant1").name("TestTenant"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest)
        .isEqualTo(
            SearchQuery.of(
                builder ->
                    builder.bool(
                        b ->
                            b.must(
                                List.of(
                                    SearchQuery.of(q -> q.term(t -> t.field("key").value(12345L))),
                                    SearchQuery.of(
                                        q -> q.term(t -> t.field("tenantId").value("tenant1"))),
                                    SearchQuery.of(
                                        q -> q.term(t -> t.field("name").value("TestTenant"))),
                                    SearchQuery.of(
                                        q -> q.term(t -> t.field("join").value("tenant"))))))));
  }

  @Test
  public void shouldApplyAuthorizationCheck() {
    // given
    final var authorization =
        Authorization.of(a -> a.tenant().read().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.tenant(b -> b), resourceAccessChecks);

    // then
    final SearchQueryOption queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            (boolQuery) -> {
              assertThat(boolQuery.must())
                  .anySatisfy(
                      query ->
                          assertThat(query.queryOption())
                              .isInstanceOfSatisfying(
                                  SearchTermsQuery.class,
                                  (termsQuery) -> {
                                    assertThat(termsQuery.field()).isEqualTo(TenantIndex.TENANT_ID);
                                    assertThat(
                                            termsQuery.values().stream()
                                                .map(TypedValue::stringValue)
                                                .toList())
                                        .containsExactlyInAnyOrder("1", "2");
                                  }));
            });
  }

  @Test
  public void shouldIgnoreAuthorizationCheckWhenDisabled() {
    // given
    final var authorizationCheck = AuthorizationCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.tenant(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery)
        .isEqualTo(SearchQuery.of(q1 -> q1.term(t -> t.field("join").value("tenant"))));
  }

  @Test
  public void shouldIgnoreTenantCheck() {
    // given
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery = transformQuery(FilterBuilders.tenant(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery)
        .isEqualTo(SearchQuery.of(q1 -> q1.term(t -> t.field("join").value("tenant"))));
  }

  @Test
  public void shouldApplyFilterAndChecks() {
    // given
    final var authorization =
        Authorization.of(a -> a.tenant().read().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.tenant(b -> b.tenantId("a")), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(SearchBoolQuery.class, t -> assertThat(t.must()).hasSize(2));
  }

  @Test
  public void shouldReturnNonMatchWhenNoResourceIdsProvided() {
    // given
    final var authorization = Authorization.of(a -> a.tenant().read());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.tenant(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }
}
