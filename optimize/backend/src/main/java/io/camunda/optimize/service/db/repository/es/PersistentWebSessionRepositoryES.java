/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.WEB_SESSION_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.WebSessionDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.PersistentWebSessionRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class PersistentWebSessionRepositoryES implements PersistentWebSessionRepository {

  // First page of the sweep scroll; retrieveAllScrollResults pages through the rest.
  private static final int SCROLL_PAGE_SIZE = 1000;

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public PersistentWebSessionRepositoryES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<WebSessionDto> get(final String sessionId) {
    try {
      // A get by id reads through the translog, so a session is visible without a prior refresh.
      final var response =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g -> g.optimizeIndex(esClient, WEB_SESSION_INDEX_NAME).id(sessionId)),
              WebSessionDto.class);
      return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read web session " + sessionId, e);
    }
  }

  @Override
  public void upsert(final WebSessionDto session) {
    try {
      esClient.index(
          OptimizeIndexRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, WEB_SESSION_INDEX_NAME)
                      .id(session.getId())
                      .document(session)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not persist web session " + session.getId(), e);
    }
  }

  @Override
  public void delete(final String sessionId) {
    try {
      esClient.delete(
          OptimizeDeleteRequestBuilderES.of(
              d -> d.optimizeIndex(esClient, WEB_SESSION_INDEX_NAME).id(sessionId)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not delete web session " + sessionId, e);
    }
  }

  @Override
  public List<WebSessionDto> getAll() {
    final int scrollTimeout =
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds();
    final var searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, WEB_SESSION_INDEX_NAME)
                    .size(SCROLL_PAGE_SIZE)
                    .query(q -> q.matchAll(MatchAllQuery.of(m -> m)))
                    .scroll(Time.of(t -> t.time(scrollTimeout + "s"))));
    final SearchResponse<WebSessionDto> response;
    try {
      response = esClient.search(searchRequest, WebSessionDto.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read web sessions", e);
    }
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        response, WebSessionDto.class, objectMapper, esClient, scrollTimeout);
  }
}
