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
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.FilterBuilders;
import org.junit.jupiter.api.Test;

public class RoleQueryTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByRoleId() {
    // given
    final var filter = FilterBuilders.role((f) -> f.roleId("role1"));

    // when
    final var query = (SearchBoolQuery) transformQuery(filter).queryOption();

    // then
    assertThat(query.filter()).isEmpty();
    assertThat(query.should()).isEmpty();
    assertThat(query.must())
        .containsExactlyInAnyOrder(
            SearchQuery.of(q1 -> q1.term(t -> t.field("roleId").value("role1"))),
            SearchQuery.of(q1 -> q1.term(t -> t.field("join").value("role"))));
  }

  @Test
  void shouldQueryByRoleName() {
    // given
    final var filter = FilterBuilders.role((f) -> f.name("roleName"));

    // when
    final var query = (SearchBoolQuery) transformQuery(filter).queryOption();

    // then
    assertThat(query.filter()).isEmpty();
    assertThat(query.should()).isEmpty();
    assertThat(query.must())
        .containsExactlyInAnyOrder(
            SearchQuery.of(q1 -> q1.term(t -> t.field("name").value("roleName"))),
            SearchQuery.of(q1 -> q1.term(t -> t.field("join").value("role"))));
  }

  @Test
  void shouldQueryByRoleDescription() {
    // given
    final var filter = FilterBuilders.role((f) -> f.description("roleDescription"));

    // when
    final var query = (SearchBoolQuery) transformQuery(filter).queryOption();

    // then
    assertThat(query.filter()).isEmpty();
    assertThat(query.should()).isEmpty();
    assertThat(query.must())
        .containsExactlyInAnyOrder(
            SearchQuery.of(q1 -> q1.term(t -> t.field("description").value("roleDescription"))),
            SearchQuery.of(q1 -> q1.term(t -> t.field("join").value("role"))));
  }

  @Test
  void shouldQueryMembersByRoleId() {
    // given
    final var filter =
        FilterBuilders.role((f) -> f.joinParentId("test-parent-id").memberType(USER));

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
  void shouldQueryRolesByMemberId() {
    // given
    final var filter =
        FilterBuilders.role((f) -> f.memberId("test-member-id").childMemberType(USER));

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
    final var joinQuery = SearchQuery.of(q1 -> q1.term(t -> t.field("join").value("role")));

    assertThat(query.filter()).isEmpty();
    assertThat(query.should()).isEmpty();
    assertThat(query.must())
        .containsExactlyInAnyOrder(
            expectedChildMemberTypeQuery, expectedChildMemberIdQuery, joinQuery);
  }

  @Test
  void shouldQueryByMultipleRoleFields() {
    // given
    final var filter = FilterBuilders.role((f) -> f.roleId("role1").name("TestRole"));

    // when
    final var query = (SearchBoolQuery) transformQuery(filter).queryOption();

    // then
    assertThat(query.filter()).isEmpty();
    assertThat(query.should()).isEmpty();
    assertThat(query.must())
        .containsExactlyInAnyOrder(
            SearchQuery.of(q -> q.term(t -> t.field("roleId").value("role1"))),
            SearchQuery.of(q -> q.term(t -> t.field("name").value("TestRole"))),
            SearchQuery.of(q -> q.term(t -> t.field("join").value("role"))));
  }
}
