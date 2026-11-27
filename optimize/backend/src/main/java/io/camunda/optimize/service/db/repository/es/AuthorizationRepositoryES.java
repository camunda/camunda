/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.AUTHORIZATION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.access.AuthorizationDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.AuthorizationRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class AuthorizationRepositoryES implements AuthorizationRepository {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public AuthorizationRepositoryES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<AuthorizationDto> get(final String id) {
    try {
      final GetResponse<Map> response =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g -> g.optimizeIndex(esClient, AUTHORIZATION_INDEX_NAME).id(id)),
              Map.class);
      return response.found()
          ? Optional.ofNullable(response.source()).map(AuthorizationDto::fromDocument)
          : Optional.empty();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read authorization " + id, e);
    }
  }

  @Override
  public List<AuthorizationDto> getAll() {
    final int scrollTimeout =
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds();
    final SearchResponse<Map> response;
    try {
      response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  s ->
                      s.optimizeIndex(esClient, AUTHORIZATION_INDEX_NAME)
                          .query(q -> q.matchAll(m -> m))
                          .size(LIST_FETCH_LIMIT)
                          .scroll(Time.of(t -> t.time(scrollTimeout + "s")))),
              Map.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read authorizations", e);
    }
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
            response, Map.class, objectMapper, esClient, scrollTimeout)
        .stream()
        .map(document -> AuthorizationDto.fromDocument((Map<String, Object>) document))
        .toList();
  }

  @Override
  public void upsert(final AuthorizationDto authorization) {
    try {
      esClient.index(
          OptimizeIndexRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, AUTHORIZATION_INDEX_NAME)
                      .id(authorization.id())
                      .refresh(Refresh.True)
                      .document(authorization.toDocument())));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Could not persist authorization " + authorization.id(), e);
    }
  }

  @Override
  public void delete(final String id) {
    try {
      esClient.delete(
          OptimizeDeleteRequestBuilderES.of(
              d ->
                  d.optimizeIndex(esClient, AUTHORIZATION_INDEX_NAME)
                      .id(id)
                      .refresh(Refresh.True)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not delete authorization " + id, e);
    }
  }
}
