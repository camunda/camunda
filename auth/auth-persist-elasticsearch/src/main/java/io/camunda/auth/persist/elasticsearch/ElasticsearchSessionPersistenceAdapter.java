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
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.auth.domain.model.SessionData;
import io.camunda.auth.domain.spi.SessionPersistencePort;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link SessionPersistencePort}. */
public class ElasticsearchSessionPersistenceAdapter implements SessionPersistencePort {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchSessionPersistenceAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-web-session";

  private final ElasticsearchClient client;
  private final String indexName;

  public ElasticsearchSessionPersistenceAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME);
  }

  public ElasticsearchSessionPersistenceAdapter(
      final ElasticsearchClient client, final String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  @Override
  public SessionData findById(final String sessionId) {
    LOG.debug("Fetching session by id={} from index={}", sessionId, indexName);
    try {
      final GetResponse<ElasticsearchSessionDocument> response =
          client.get(
              request -> request.index(indexName).id(sessionId),
              ElasticsearchSessionDocument.class);
      if (response.found() && response.source() != null) {
        return response.source().toDomain();
      }
      return null;
    } catch (final ElasticsearchException e) {
      throw new RuntimeException("Failed to get session with id=" + sessionId, e);
    } catch (final IOException e) {
      throw new RuntimeException("I/O error fetching session with id=" + sessionId, e);
    }
  }

  @Override
  public void save(final SessionData sessionData) {
    LOG.debug("Saving session id={} to index={}", sessionData.id(), indexName);
    try {
      final ElasticsearchSessionDocument document =
          ElasticsearchSessionDocument.fromDomain(sessionData);
      client.index(request -> request.index(indexName).id(sessionData.id()).document(document));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException("Failed to index session with id=" + sessionData.id(), e);
    } catch (final IOException e) {
      throw new RuntimeException("I/O error indexing session with id=" + sessionData.id(), e);
    }
  }

  @Override
  public void deleteById(final String sessionId) {
    LOG.debug("Deleting session id={} from index={}", sessionId, indexName);
    try {
      client.delete(request -> request.index(indexName).id(sessionId));
    } catch (final ElasticsearchException e) {
      LOG.warn("Failed to delete session with id={}: {}", sessionId, e.getMessage());
    } catch (final IOException e) {
      throw new RuntimeException("I/O error deleting session with id=" + sessionId, e);
    }
  }

  @Override
  public List<SessionData> findAll() {
    LOG.debug("Fetching all sessions from index={}", indexName);
    try {
      final SearchResponse<ElasticsearchSessionDocument> response =
          client.search(
              request -> request.index(indexName).query(q -> q.matchAll(m -> m)).size(10_000),
              ElasticsearchSessionDocument.class);
      return response.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .map(ElasticsearchSessionDocument::toDomain)
          .collect(Collectors.toList());
    } catch (final ElasticsearchException e) {
      throw new RuntimeException("Failed to search sessions in index=" + indexName, e);
    } catch (final IOException e) {
      throw new RuntimeException("I/O error searching sessions in index=" + indexName, e);
    }
  }
}
