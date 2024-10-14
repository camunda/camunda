/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.CollectionReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class CollectionReaderES implements CollectionReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(CollectionReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public CollectionReaderES(
      OptimizeElasticsearchClient esClient,
      ConfigurationService configurationService,
      ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<CollectionDefinitionDto> getCollection(String collectionId) {
    log.debug("Fetching collection with id [{}]", collectionId);
    GetRequest getRequest =
        OptimizeGetRequestBuilderES.of(
            b -> b.optimizeIndex(esClient, COLLECTION_INDEX_NAME).id(collectionId));
    try {
      return Optional.ofNullable(esClient.get(getRequest, CollectionDefinitionDto.class).source());
    } catch (IOException e) {
      String reason = String.format("Could not fetch collection with id [%s]", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  public List<CollectionDefinitionDto> getAllCollections() {
    log.debug("Fetching all available collections");

    SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, COLLECTION_INDEX_NAME)
                    .query(q -> q.matchAll(m -> m))
                    .sort(
                        s ->
                            s.field(
                                f ->
                                    f.field(CollectionDefinitionDto.Fields.name.name())
                                        .order(SortOrder.Asc)))
                    .size(LIST_FETCH_LIMIT)
                    .scroll(
                        s ->
                            s.time(
                                configurationService
                                        .getElasticSearchConfiguration()
                                        .getScrollTimeoutInSeconds()
                                    + "s")));

    SearchResponse<CollectionDefinitionDto> scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, CollectionDefinitionDto.class);
    } catch (IOException e) {
      log.error("Was not able to retrieve collections!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve collections!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        scrollResp,
        CollectionDefinitionDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }
}
