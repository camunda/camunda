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
import io.camunda.auth.domain.model.AuthorizationRecord;
import io.camunda.auth.domain.port.outbound.AuthorizationReadPort;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link AuthorizationReadPort}. */
public class ElasticsearchAuthorizationReadAdapter implements AuthorizationReadPort {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchAuthorizationReadAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-authorization";

  private final ElasticsearchClient client;
  private final String indexName;

  public ElasticsearchAuthorizationReadAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME);
  }

  public ElasticsearchAuthorizationReadAdapter(
      final ElasticsearchClient client, final String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  @Override
  public List<AuthorizationRecord> findByOwner(final String ownerId, final String ownerType) {
    LOG.debug(
        "Searching authorizations for ownerId={}, ownerType={} in index={}",
        ownerId,
        ownerType,
        indexName);
    try {
      final Query ownerIdQuery =
          Query.of(q -> q.term(t -> t.field("ownerId").value(ownerId)));
      final Query ownerTypeQuery =
          Query.of(q -> q.term(t -> t.field("ownerType").value(ownerType)));
      final BoolQuery boolQuery =
          BoolQuery.of(b -> b.filter(ownerIdQuery).filter(ownerTypeQuery));

      final SearchResponse<AuthorizationRecord> response =
          client.search(
              request ->
                  request
                      .index(indexName)
                      .query(q -> q.bool(boolQuery))
                      .size(10_000),
              AuthorizationRecord.class);

      return response.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .collect(Collectors.toList());
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to search authorizations for ownerId=" + ownerId
              + ", ownerType=" + ownerType + " in index=" + indexName,
          e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error searching authorizations for ownerId=" + ownerId
              + ", ownerType=" + ownerType + " in index=" + indexName,
          e);
    }
  }

  @Override
  public List<AuthorizationRecord> findByOwnerAndResourceType(
      final String ownerId, final String ownerType, final String resourceType) {
    LOG.debug(
        "Searching authorizations for ownerId={}, ownerType={}, resourceType={} in index={}",
        ownerId,
        ownerType,
        resourceType,
        indexName);
    try {
      final Query ownerIdQuery =
          Query.of(q -> q.term(t -> t.field("ownerId").value(ownerId)));
      final Query ownerTypeQuery =
          Query.of(q -> q.term(t -> t.field("ownerType").value(ownerType)));
      final Query resourceTypeQuery =
          Query.of(q -> q.term(t -> t.field("resourceType").value(resourceType)));
      final BoolQuery boolQuery =
          BoolQuery.of(
              b -> b.filter(ownerIdQuery).filter(ownerTypeQuery).filter(resourceTypeQuery));

      final SearchResponse<AuthorizationRecord> response =
          client.search(
              request ->
                  request
                      .index(indexName)
                      .query(q -> q.bool(boolQuery))
                      .size(10_000),
              AuthorizationRecord.class);

      return response.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .collect(Collectors.toList());
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to search authorizations for ownerId=" + ownerId
              + ", ownerType=" + ownerType
              + ", resourceType=" + resourceType + " in index=" + indexName,
          e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error searching authorizations for ownerId=" + ownerId
              + ", ownerType=" + ownerType
              + ", resourceType=" + resourceType + " in index=" + indexName,
          e);
    }
  }
}
