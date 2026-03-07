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
import io.camunda.auth.domain.model.AuthorizationRecord;
import io.camunda.auth.domain.port.outbound.AuthorizationWritePort;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link AuthorizationWritePort}. */
public class ElasticsearchAuthorizationWriteAdapter implements AuthorizationWritePort {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchAuthorizationWriteAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-authorization";

  private final ElasticsearchClient client;
  private final String indexName;

  public ElasticsearchAuthorizationWriteAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME);
  }

  public ElasticsearchAuthorizationWriteAdapter(
      final ElasticsearchClient client, final String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  @Override
  public void save(final AuthorizationRecord record) {
    LOG.debug(
        "Indexing authorization with authorizationKey={} into index={}",
        record.authorizationKey(),
        indexName);
    try {
      client.index(
          request ->
              request
                  .index(indexName)
                  .id(String.valueOf(record.authorizationKey()))
                  .document(record));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to index authorization with authorizationKey=" + record.authorizationKey(), e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error indexing authorization with authorizationKey=" + record.authorizationKey(), e);
    }
  }

  @Override
  public void deleteByKey(final long authorizationKey) {
    LOG.debug(
        "Deleting authorization by authorizationKey={} from index={}", authorizationKey, indexName);
    try {
      client.delete(request -> request.index(indexName).id(String.valueOf(authorizationKey)));
    } catch (final ElasticsearchException e) {
      LOG.warn(
          "Failed to delete authorization with authorizationKey={}: {}",
          authorizationKey,
          e.getMessage());
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error deleting authorization with authorizationKey="
              + authorizationKey
              + " from index="
              + indexName,
          e);
    }
  }
}
