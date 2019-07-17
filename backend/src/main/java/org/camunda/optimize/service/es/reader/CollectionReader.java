/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.es.schema.type.CollectionType.DATA;
import static org.camunda.optimize.service.es.schema.type.CollectionType.NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;

@RequiredArgsConstructor
@Component
@Slf4j
public class CollectionReader {
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public SimpleCollectionDefinitionDto getCollection(String collectionId) {
    log.debug("Fetching collection with id [{}]", collectionId);
    GetRequest getRequest = new GetRequest(COLLECTION_TYPE, COLLECTION_TYPE, collectionId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch collection with id [%s]", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, SimpleCollectionDefinitionDto.class);
      } catch (IOException e) {
        String reason = "Could not deserialize collection information for collection " + collectionId;
        log.error("Was not able to retrieve collection with id [{}] from Elasticsearch. Reason: reason");
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      log.error("Was not able to retrieve collection with id [{}] from Elasticsearch.", collectionId);
      throw new NotFoundException("Collection does not exist! Tried to retried collection with id " + collectionId);
    }
  }

  public List<ResolvedCollectionDefinitionDto> getAllResolvedCollections() {
    final List<SimpleCollectionDefinitionDto> allCollections = getAllCollections();
    final Map<String, CollectionEntity> entityIdToEntityMap = getAllEntities()
      .stream()
      .collect(toMap(CollectionEntity::getId, r -> r));

    log.debug("Mapping all available entity collections to resolved entity collections.");
    return allCollections.stream()
      .map(c -> mapToResolvedCollection(c, entityIdToEntityMap))
      .collect(Collectors.toList());
  }

  private List<CollectionEntity> getAllEntities() {
    log.debug("Fetching all available entities for collections");
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
      new SearchRequest(SINGLE_PROCESS_REPORT_TYPE, SINGLE_DECISION_REPORT_TYPE, COMBINED_REPORT_TYPE, DASHBOARD_TYPE)
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve collection entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve entities!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      CollectionEntity.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  private ResolvedCollectionDefinitionDto mapToResolvedCollection(SimpleCollectionDefinitionDto collectionDefinitionDto,
                                                                  Map<String, CollectionEntity> entityIdToEntityMap) {
    final ResolvedCollectionDefinitionDto resolvedCollection = new ResolvedCollectionDefinitionDto();
    resolvedCollection.setId(collectionDefinitionDto.getId());
    resolvedCollection.setName(collectionDefinitionDto.getName());
    resolvedCollection.setLastModifier(collectionDefinitionDto.getLastModifier());
    resolvedCollection.setOwner(collectionDefinitionDto.getOwner());
    resolvedCollection.setCreated(collectionDefinitionDto.getCreated());
    resolvedCollection.setLastModified(collectionDefinitionDto.getLastModified());

    if (collectionDefinitionDto.getData() != null) {
      final CollectionDataDto<String> collectionData = collectionDefinitionDto.getData();
      final CollectionDataDto<CollectionEntity> resolvedCollectionData = new CollectionDataDto<>();
      resolvedCollectionData.setConfiguration(collectionDefinitionDto.getData().getConfiguration());
      resolvedCollectionData.setEntities(
        collectionData.getEntities()
          .stream()
          .map(entityIdToEntityMap::get)
          .filter(Objects::nonNull)
          .sorted(Comparator.comparing(CollectionEntity::getLastModified).reversed())
          .collect(Collectors.toList())
      );
      resolvedCollection.setData(resolvedCollectionData);
    }
    return resolvedCollection;
  }

  private List<SimpleCollectionDefinitionDto> getAllCollections() {
    log.debug("Fetching all available collections");

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .sort(NAME, SortOrder.ASC)
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(COLLECTION_TYPE)
      .types(COLLECTION_TYPE)
      .source(searchSourceBuilder)
      .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve collections!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve collections!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      SimpleCollectionDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  public List<SimpleCollectionDefinitionDto> findFirstCollectionsForEntity(String entityId) {
    log.debug("Fetching collections using entity with id {}", entityId);

    final QueryBuilder getCollectionByEntityIdQuery = QueryBuilders.boolQuery()
      .filter(QueryBuilders.nestedQuery(
        DATA,
        QueryBuilders.termQuery("data.entities", entityId),
        ScoreMode.None
      ));
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(getCollectionByEntityIdQuery)
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(COLLECTION_TYPE)
      .types(COLLECTION_TYPE)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch collections for entity with id [%s]", entityId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchHelper.mapHits(searchResponse.getHits(), SimpleCollectionDefinitionDto.class, objectMapper);
  }
}
