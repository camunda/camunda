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
import io.camunda.auth.domain.model.AuthGroup;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.outbound.GroupReadPort;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link GroupReadPort}. */
public class ElasticsearchGroupReadAdapter implements GroupReadPort {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchGroupReadAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-group";
  private static final String DEFAULT_MEMBER_INDEX_NAME = "camunda-auth-group-member";

  private final ElasticsearchClient client;
  private final String indexName;
  private final String memberIndexName;

  public ElasticsearchGroupReadAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME, DEFAULT_MEMBER_INDEX_NAME);
  }

  public ElasticsearchGroupReadAdapter(
      final ElasticsearchClient client, final String indexName, final String memberIndexName) {
    this.client = client;
    this.indexName = indexName;
    this.memberIndexName = memberIndexName;
  }

  @Override
  public Optional<AuthGroup> findById(final String groupId) {
    LOG.debug("Searching group by groupId={} in index={}", groupId, indexName);
    try {
      final SearchResponse<AuthGroup> response =
          client.search(
              request ->
                  request
                      .index(indexName)
                      .query(q -> q.term(t -> t.field("groupId").value(groupId)))
                      .size(1),
              AuthGroup.class);
      return response.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .findFirst();
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to search group by groupId=" + groupId + " in index=" + indexName, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error searching group by groupId=" + groupId + " in index=" + indexName, e);
    }
  }

  @Override
  public List<AuthGroup> findByMember(final String memberId, final MemberType memberType) {
    LOG.debug(
        "Searching groups for memberId={}, memberType={} in index={}",
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

      final List<String> groupIds =
          memberResponse.hits().hits().stream()
              .map(Hit::source)
              .filter(doc -> doc != null)
              .map(doc -> (String) doc.get("groupId"))
              .filter(id -> id != null)
              .collect(Collectors.toList());

      if (groupIds.isEmpty()) {
        return Collections.emptyList();
      }

      // Step 2: batch-fetch groups by their groupIds
      final SearchResponse<AuthGroup> groupResponse =
          client.search(
              request ->
                  request
                      .index(indexName)
                      .query(
                          q ->
                              q.terms(
                                  t ->
                                      t.field("groupId")
                                          .terms(
                                              v ->
                                                  v.value(
                                                      groupIds.stream()
                                                          .map(
                                                              co.elastic.clients.elasticsearch
                                                                      ._types.FieldValue
                                                                  ::of)
                                                          .collect(Collectors.toList())))))
                      .size(groupIds.size()),
              AuthGroup.class);

      return groupResponse.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .collect(Collectors.toList());
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to search groups for memberId=" + memberId + ", memberType=" + memberType, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error searching groups for memberId=" + memberId + ", memberType=" + memberType, e);
    }
  }
}
