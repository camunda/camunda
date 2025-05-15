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
import java.util.List;
import org.junit.jupiter.api.Test;

public class GroupQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByGroupKey() {
    // given
    final var filter = FilterBuilders.group((f) -> f.groupKey(12345L));

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
                                        q1 -> q.term(t -> t.field("join").value("group"))))))));
  }

  @Test
  public void shouldQueryByGroupId() {
    // given
    final var filter = FilterBuilders.group((f) -> f.groupId("group1"));

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
                                        q1 -> q.term(t -> t.field("groupId").value("group1"))),
                                    SearchQuery.of(
                                        q1 -> q.term(t -> t.field("join").value("group"))))))));
  }

  @Test
  public void shouldQueryByGroupName() {
    // given
    final var filter = FilterBuilders.group((f) -> f.name("groupName"));

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
                                        q1 -> q.term(t -> t.field("name").value("groupName"))),
                                    SearchQuery.of(
                                        q1 -> q.term(t -> t.field("join").value("group"))))))));
  }

  @Test
  public void shouldQueryByGroupDescription() {
    // given
    final var filter = FilterBuilders.group((f) -> f.description("groupDescription"));

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
                                        q1 ->
                                            q.term(
                                                t ->
                                                    t.field("description")
                                                        .value("groupDescription"))),
                                    SearchQuery.of(
                                        q1 -> q.term(t -> t.field("join").value("group"))))))));
  }

  @Test
  public void shouldQueryMembersByGroupId() {
    // given
    final var filter =
        FilterBuilders.group((f) -> f.joinParentId("test-parent-id").memberType(USER));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    assertThat(searchRequest)
        .isEqualTo(generateSearchQueryForParent("test-parent-id", USER.name()));
  }

  @Test
  void shouldQueryGroupsByMemberId() {
    // given
    final var filter =
        FilterBuilders.group((f) -> f.memberId("test-member-id").childMemberType(USER));

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
    final var joinQuery = SearchQuery.of(q1 -> q1.term(t -> t.field("join").value("group")));

    assertThat(query.filter()).isEmpty();
    assertThat(query.should()).isEmpty();
    assertThat(query.must())
        .containsExactlyInAnyOrder(
            expectedChildMemberTypeQuery, expectedChildMemberIdQuery, joinQuery);
  }

  @Test
  public void shouldQueryByMultipleGroupFields() {
    // given
    final var filter =
        FilterBuilders.group((f) -> f.groupKey(12345L).groupId("group1").name("TestGroup"));

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
                                        q -> q.term(t -> t.field("groupId").value("group1"))),
                                    SearchQuery.of(
                                        q -> q.term(t -> t.field("name").value("TestGroup"))),
                                    SearchQuery.of(
                                        q -> q.term(t -> t.field("join").value("group"))))))));
  }

  private SearchQuery generateSearchQueryForParent(final String parentId, final String memberType) {
    return SearchQuery.of(
        q ->
            q.bool(
                b ->
                    b.must(
                        List.of(
                            SearchQuery.of(
                                q1 -> q1.term(t -> t.field("memberType").value(memberType))),
                            SearchQuery.of(
                                q1 ->
                                    q1.hasParent(
                                        p ->
                                            p.parentType("group")
                                                .query(
                                                    SearchQuery.of(
                                                        q2 ->
                                                            q2.term(
                                                                t ->
                                                                    t.field("groupId")
                                                                        .value(parentId))))))))));
  }
}
