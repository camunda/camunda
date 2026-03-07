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
import io.camunda.auth.domain.model.AuthUser;
import io.camunda.auth.domain.port.outbound.UserReadPort;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link UserReadPort}. */
public class ElasticsearchUserReadAdapter implements UserReadPort {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchUserReadAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-user";

  private final ElasticsearchClient client;
  private final String indexName;

  public ElasticsearchUserReadAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME);
  }

  public ElasticsearchUserReadAdapter(
      final ElasticsearchClient client, final String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  @Override
  public Optional<AuthUser> findByUsername(final String username) {
    LOG.debug("Searching user by username={} in index={}", username, indexName);
    try {
      final SearchResponse<AuthUser> response =
          client.search(
              request ->
                  request
                      .index(indexName)
                      .query(q -> q.term(t -> t.field("username").value(username)))
                      .size(1),
              AuthUser.class);
      return response.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .findFirst();
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to search user by username=" + username + " in index=" + indexName, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error searching user by username=" + username + " in index=" + indexName, e);
    }
  }

  @Override
  public Optional<AuthUser> findByKey(final long userKey) {
    LOG.debug("Fetching user by key={} from index={}", userKey, indexName);
    try {
      final GetResponse<AuthUser> response =
          client.get(
              request -> request.index(indexName).id(String.valueOf(userKey)), AuthUser.class);
      if (response.found() && response.source() != null) {
        return Optional.of(response.source());
      }
      return Optional.empty();
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to get user with key=" + userKey + " from index=" + indexName, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error fetching user with key=" + userKey + " from index=" + indexName, e);
    }
  }
}
