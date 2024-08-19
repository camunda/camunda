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
import static org.elasticsearch.core.TimeValue.timeValueSeconds;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.CollectionReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
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
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<CollectionDefinitionDto> getCollection(final String collectionId) {
    log.debug("Fetching collection with id [{}]", collectionId);
    final GetRequest getRequest = new GetRequest(COLLECTION_INDEX_NAME).id(collectionId);

    final GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (final IOException e) {
      final String reason = String.format("Could not fetch collection with id [%s]", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!getResponse.isExists()) {
      return Optional.empty();
    }

    final String responseAsString = getResponse.getSourceAsString();
    try {
      return Optional.ofNullable(
          objectMapper.readValue(responseAsString, CollectionDefinitionDto.class));
    } catch (final IOException e) {
      final String reason =
          "Could not deserialize collection information for collection " + collectionId;
      log.error(
          "Was not able to retrieve collection with id [{}] from Elasticsearch. Reason: {}",
          collectionId,
          reason);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  public List<CollectionDefinitionDto> getAllCollections() {
    log.debug("Fetching all available collections");

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery())
            .sort(CollectionDefinitionDto.Fields.name.name(), SortOrder.ASC)
            .size(LIST_FETCH_LIMIT);
    final SearchRequest searchRequest =
        new SearchRequest(COLLECTION_INDEX_NAME)
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (final IOException e) {
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
