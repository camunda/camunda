/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.WEB_SESSION_INDEX_NAME;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.query.WebSessionDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.PersistentWebSessionRepository;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class PersistentWebSessionRepositoryOS implements PersistentWebSessionRepository {

  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;

  public PersistentWebSessionRepositoryOS(
      final OptimizeOpenSearchClient osClient, final OptimizeIndexNameService indexNameService) {
    this.osClient = osClient;
    this.indexNameService = indexNameService;
  }

  @Override
  public Optional<WebSessionDto> get(final String sessionId) {
    // A get by id reads through the translog, so a session is visible without a prior refresh.
    final GetRequest.Builder requestBuilder =
        new GetRequest.Builder().index(webSessionIndexAlias()).id(sessionId);
    final GetResponse<WebSessionDto> response =
        osClient.get(
            requestBuilder,
            WebSessionDto.class,
            format("Could not read web session %s", sessionId));
    return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
  }

  @Override
  public void upsert(final WebSessionDto session) {
    final IndexRequest.Builder<WebSessionDto> requestBuilder =
        new IndexRequest.Builder<WebSessionDto>()
            .index(webSessionIndexAlias())
            .id(session.getId())
            .document(session);
    osClient.index(requestBuilder);
  }

  @Override
  public void delete(final String sessionId) {
    osClient.delete(webSessionIndexAlias(), sessionId);
  }

  @Override
  public List<WebSessionDto> getAll() {
    // Scrolled rather than a plain search: the sweep must see every session, not the first page.
    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(webSessionIndexAlias())
            .query(matchAll())
            .size(LIST_FETCH_LIMIT);
    return osClient.scrollValues(requestBuilder, WebSessionDto.class);
  }

  private String webSessionIndexAlias() {
    return indexNameService.getOptimizeIndexAliasForIndex(WEB_SESSION_INDEX_NAME);
  }
}
