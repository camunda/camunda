/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.core.SearchDeleteRequest;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.webapps.schema.descriptors.index.PersistentWebSessionIndexDescriptor;

public class PersistentWebSessionSearchImpl implements PersistentWebSessionClient {

  private final DocumentBasedSearchClient readClient;
  private final DocumentBasedWriteClient writeClient;
  private final PersistentWebSessionIndexDescriptor persistentWebSessionIndex;

  public PersistentWebSessionSearchImpl(
      final DocumentBasedSearchClient readClient,
      final DocumentBasedWriteClient writeClient,
      final PersistentWebSessionIndexDescriptor persistentWebSessionIndex) {
    this.readClient = readClient;
    this.writeClient = writeClient;
    this.persistentWebSessionIndex = persistentWebSessionIndex;
  }

  @Override
  public PersistentWebSessionEntity getPersistentWebSession(final String sessionId) {
    final var request =
        SearchGetRequest.of(
            b -> b.id(sessionId).index(persistentWebSessionIndex.getFullQualifiedName()));
    final var session = readClient.get(request, PersistentWebSessionEntity.class);
    return session.source();
  }

  @Override
  public void upsertPersistentWebSession(
      final PersistentWebSessionEntity persistentWebSessionEntity) {
    writeClient.index(
        SearchIndexRequest.of(
            b ->
                b.id(persistentWebSessionEntity.id())
                    .index(persistentWebSessionIndex.getFullQualifiedName())
                    .document(persistentWebSessionEntity)));
  }

  @Override
  public void deletePersistentWebSession(final String sessionId) {
    writeClient.delete(
        SearchDeleteRequest.of(
            b -> b.id(sessionId).index(persistentWebSessionIndex.getFullQualifiedName())));
  }

  @Override
  public SearchQueryResult<PersistentWebSessionEntity> getAllPersistentWebSessions() {
    final var response =
        readClient.scroll(
            SearchQueryRequest.of(b -> b.index(persistentWebSessionIndex.getFullQualifiedName())),
            PersistentWebSessionEntity.class);
    return SearchQueryResult.of(
        r -> r.items(response.hits().stream().map(SearchQueryHit::source).toList()));
  }
}
