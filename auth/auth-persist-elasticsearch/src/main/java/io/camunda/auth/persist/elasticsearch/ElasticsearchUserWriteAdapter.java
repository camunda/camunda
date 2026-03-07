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
import io.camunda.auth.domain.model.AuthUser;
import io.camunda.auth.domain.port.outbound.UserWritePort;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link UserWritePort}. */
public class ElasticsearchUserWriteAdapter implements UserWritePort {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchUserWriteAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-user";

  private final ElasticsearchClient client;
  private final String indexName;

  public ElasticsearchUserWriteAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME);
  }

  public ElasticsearchUserWriteAdapter(final ElasticsearchClient client, final String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  @Override
  public void save(final AuthUser user) {
    LOG.debug("Indexing user with userKey={} into index={}", user.userKey(), indexName);
    try {
      client.index(
          request -> request.index(indexName).id(String.valueOf(user.userKey())).document(user));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException("Failed to index user with userKey=" + user.userKey(), e);
    } catch (final IOException e) {
      throw new RuntimeException("I/O error indexing user with userKey=" + user.userKey(), e);
    }
  }

  @Override
  public void deleteByUsername(final String username) {
    LOG.debug("Deleting user by username={} from index={}", username, indexName);
    try {
      client.deleteByQuery(
          request ->
              request
                  .index(indexName)
                  .query(q -> q.term(t -> t.field("username").value(username))));
    } catch (final ElasticsearchException e) {
      LOG.warn("Failed to delete user with username={}: {}", username, e.getMessage());
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error deleting user with username=" + username + " from index=" + indexName, e);
    }
  }
}
