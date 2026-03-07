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
import io.camunda.auth.domain.model.AuthTenant;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.outbound.TenantReadPort;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link TenantReadPort}. */
public class ElasticsearchTenantReadAdapter implements TenantReadPort {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchTenantReadAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-tenant";
  private static final String DEFAULT_MEMBER_INDEX_NAME = "camunda-auth-tenant-member";

  private final ElasticsearchClient client;
  private final String indexName;
  private final String memberIndexName;

  public ElasticsearchTenantReadAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME, DEFAULT_MEMBER_INDEX_NAME);
  }

  public ElasticsearchTenantReadAdapter(
      final ElasticsearchClient client, final String indexName, final String memberIndexName) {
    this.client = client;
    this.indexName = indexName;
    this.memberIndexName = memberIndexName;
  }

  @Override
  public Optional<AuthTenant> findById(final String tenantId) {
    LOG.debug("Searching tenant by tenantId={} in index={}", tenantId, indexName);
    try {
      final SearchResponse<AuthTenant> response =
          client.search(
              request ->
                  request
                      .index(indexName)
                      .query(q -> q.term(t -> t.field("tenantId").value(tenantId)))
                      .size(1),
              AuthTenant.class);
      return response.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .findFirst();
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to search tenant by tenantId=" + tenantId + " in index=" + indexName, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error searching tenant by tenantId=" + tenantId + " in index=" + indexName, e);
    }
  }

  @Override
  public List<AuthTenant> findByMember(final String memberId, final MemberType memberType) {
    LOG.debug(
        "Searching tenants for memberId={}, memberType={} in index={}",
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

      final List<String> tenantIds =
          memberResponse.hits().hits().stream()
              .map(Hit::source)
              .filter(doc -> doc != null)
              .map(doc -> (String) doc.get("tenantId"))
              .filter(id -> id != null)
              .collect(Collectors.toList());

      if (tenantIds.isEmpty()) {
        return Collections.emptyList();
      }

      // Step 2: batch-fetch tenants by their tenantIds
      final SearchResponse<AuthTenant> tenantResponse =
          client.search(
              request ->
                  request
                      .index(indexName)
                      .query(
                          q ->
                              q.terms(
                                  t ->
                                      t.field("tenantId")
                                          .terms(
                                              v ->
                                                  v.value(
                                                      tenantIds.stream()
                                                          .map(
                                                              co.elastic.clients.elasticsearch
                                                                      ._types.FieldValue
                                                                  ::of)
                                                          .collect(Collectors.toList())))))
                      .size(tenantIds.size()),
              AuthTenant.class);

      return tenantResponse.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .collect(Collectors.toList());
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to search tenants for memberId=" + memberId + ", memberType=" + memberType, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error searching tenants for memberId=" + memberId + ", memberType=" + memberType, e);
    }
  }
}
