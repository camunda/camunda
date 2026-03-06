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
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link TokenStorePort}. */
public class ElasticsearchTokenStoreAdapter implements TokenStorePort {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchTokenStoreAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-token-exchange-audit";

  private final ElasticsearchClient client;
  private final String indexName;

  public ElasticsearchTokenStoreAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME);
  }

  public ElasticsearchTokenStoreAdapter(final ElasticsearchClient client, final String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  @Override
  public void store(final TokenMetadata metadata) {
    LOG.debug(
        "Indexing token exchange audit document with exchangeId={} into index={}",
        metadata.exchangeId(),
        indexName);
    try {
      final ElasticsearchTokenDocument document = ElasticsearchTokenDocument.fromDomain(metadata);
      client.index(
          request -> request.index(indexName).id(metadata.exchangeId()).document(document));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to index token exchange audit document with exchangeId=" + metadata.exchangeId(),
          e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error indexing token exchange audit document with exchangeId="
              + metadata.exchangeId(),
          e);
    }
  }

  @Override
  public Optional<TokenMetadata> findByExchangeId(final String exchangeId) {
    LOG.debug(
        "Fetching token exchange audit document by exchangeId={} from index={}",
        exchangeId,
        indexName);
    try {
      final GetResponse<ElasticsearchTokenDocument> response =
          client.get(
              request -> request.index(indexName).id(exchangeId), ElasticsearchTokenDocument.class);
      if (response.found() && response.source() != null) {
        return Optional.of(response.source().toDomain());
      }
      return Optional.empty();
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to get token exchange audit document with exchangeId=" + exchangeId, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error fetching token exchange audit document with exchangeId=" + exchangeId, e);
    }
  }

  @Override
  public List<TokenMetadata> findBySubjectPrincipalId(
      final String subjectPrincipalId, final Instant from, final Instant to) {
    LOG.debug(
        "Searching token exchange audit documents for subjectPrincipalId={} between {} and {}"
            + " in index={}",
        subjectPrincipalId,
        from,
        to,
        indexName);
    try {
      final Query subjectQuery =
          Query.of(q -> q.term(t -> t.field("subjectPrincipalId").value(subjectPrincipalId)));
      final Query rangeQuery =
          Query.of(
              q ->
                  q.range(
                      r ->
                          r.untyped(
                              u ->
                                  u.field("exchangeTime")
                                      .gte(co.elastic.clients.json.JsonData.of(from.toEpochMilli()))
                                      .lte(
                                          co.elastic.clients.json.JsonData.of(
                                              to.toEpochMilli())))));

      final BoolQuery boolQuery = BoolQuery.of(b -> b.filter(subjectQuery).filter(rangeQuery));

      final SearchResponse<ElasticsearchTokenDocument> response =
          client.search(
              request -> request.index(indexName).query(q -> q.bool(boolQuery)).size(10_000),
              ElasticsearchTokenDocument.class);

      return response.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .map(ElasticsearchTokenDocument::toDomain)
          .collect(Collectors.toList());
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to search token exchange audit documents for subjectPrincipalId="
              + subjectPrincipalId,
          e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error searching token exchange audit documents for subjectPrincipalId="
              + subjectPrincipalId,
          e);
    }
  }
}
