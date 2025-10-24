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
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RoleMemberQueryTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryMembersByRoleId() {
    // given
    final var filter =
        FilterBuilders.roleMember((f) -> f.roleId("test-parent-id").memberType(USER));

    // when
    final var query = (SearchBoolQuery) transformQuery(filter).queryOption();

    // then
    final var expectedMemberTypeQuery =
        SearchQuery.of(q1 -> q1.term(t -> t.field("memberType").value(USER.name())));
    final var roleIdQuery =
        SearchQuery.of(q1 -> q1.term(t -> t.field("roleId").value("test-parent-id")));
    final var expectedParentQuery =
        SearchQuery.of(q -> q.hasParent(hp -> hp.parentType("role").query(roleIdQuery)));

    assertThat(query.filter()).isEmpty();
    assertThat(query.should()).isEmpty();
    assertThat(query.must())
        .containsExactlyInAnyOrder(expectedMemberTypeQuery, expectedParentQuery);
  }

  @Test
  public void shouldApplyAuthorizationCheck() {
    // given
    final var authorization = Authorization.of(a -> a.role().read().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.roleMember(b -> b), resourceAccessChecks);

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
                                    assertThat(termsQuery.field()).isEqualTo(RoleIndex.ROLE_ID);
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
    final var searchQuery = transformQuery(FilterBuilders.roleMember(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery)
        .isEqualTo(SearchQuery.of(q1 -> q1.term(t -> t.field("join").value("role"))));
  }

  @Test
  public void shouldIgnoreTenantCheck() {
    // given
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery = transformQuery(FilterBuilders.roleMember(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery)
        .isEqualTo(SearchQuery.of(q1 -> q1.term(t -> t.field("join").value("role"))));
  }

  @Test
  public void shouldApplyFilterAndChecks() {
    // given
    final var authorization = Authorization.of(a -> a.role().read().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.roleMember(b -> b.roleId("a")), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(SearchBoolQuery.class, t -> assertThat(t.must()).hasSize(2));
  }

  @Test
  public void shouldReturnNonMatchWhenNoResourceIdsProvided() {
    // given
    final var authorization = Authorization.of(a -> a.role().read());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.roleMember(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }
}
