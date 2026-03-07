/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.auth.domain.model.AuthRole;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.outbound.RoleReadPort;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link RoleReadPort}. */
public class ElasticsearchRoleReadAdapter implements RoleReadPort {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchRoleReadAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-role";
  private static final String DEFAULT_MEMBER_INDEX_NAME = "camunda-auth-role-member";

  private final ElasticsearchClient client;
  private final String indexName;
  private final String memberIndexName;

  public ElasticsearchRoleReadAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME, DEFAULT_MEMBER_INDEX_NAME);
  }

  public ElasticsearchRoleReadAdapter(
      final ElasticsearchClient client, final String indexName, final String memberIndexName) {
    this.client = client;
    this.indexName = indexName;
    this.memberIndexName = memberIndexName;
  }

  @Override
  public Optional<AuthRole> findById(final String roleId) {
    LOG.debug("Searching role by roleId={} in index={}", roleId, indexName);
    try {
      final SearchResponse<AuthRole> response =
          client.search(
              request ->
                  request
                      .index(indexName)
                      .query(q -> q.term(t -> t.field("roleId").value(roleId)))
                      .size(1),
              AuthRole.class);
      return response.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .findFirst();
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to search role by roleId=" + roleId + " in index=" + indexName, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error searching role by roleId=" + roleId + " in index=" + indexName, e);
    }
  }

  @Override
  public List<AuthRole> findByMember(final String memberId, final MemberType memberType) {
    LOG.debug(
        "Searching roles for memberId={}, memberType={} in index={}",
        memberId,
        memberType,
        memberIndexName);
    try {
      // Step 1: find membership documents matching memberId and memberType
      final Query memberIdQuery = Query.of(q -> q.term(t -> t.field("memberId").value(memberId)));
      final Query memberTypeQuery =
          Query.of(q -> q.term(t -> t.field("memberType").value(memberType.name())));
      final BoolQuery boolQuery =
          BoolQuery.of(b -> b.filter(memberIdQuery).filter(memberTypeQuery));

      final SearchResponse<Map> memberResponse =
          client.search(
              request -> request.index(memberIndexName).query(q -> q.bool(boolQuery)).size(10_000),
              Map.class);

      final List<String> roleIds =
          memberResponse.hits().hits().stream()
              .map(Hit::source)
              .filter(doc -> doc != null)
              .map(doc -> (String) doc.get("roleId"))
              .filter(id -> id != null)
              .collect(Collectors.toList());

      if (roleIds.isEmpty()) {
        return Collections.emptyList();
      }

      // Step 2: batch-fetch roles by their roleIds
      final SearchResponse<AuthRole> roleResponse =
          client.search(
              request ->
                  request
                      .index(indexName)
                      .query(
                          q ->
                              q.terms(
                                  t ->
                                      t.field("roleId")
                                          .terms(
                                              v ->
                                                  v.value(
                                                      roleIds.stream()
                                                          .map(
                                                              co.elastic.clients.elasticsearch
                                                                      ._types.FieldValue
                                                                  ::of)
                                                          .collect(Collectors.toList())))))
                      .size(roleIds.size()),
              AuthRole.class);

      return roleResponse.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .collect(Collectors.toList());
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to search roles for memberId=" + memberId + ", memberType=" + memberType, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error searching roles for memberId=" + memberId + ", memberType=" + memberType, e);
    }
  }
}
