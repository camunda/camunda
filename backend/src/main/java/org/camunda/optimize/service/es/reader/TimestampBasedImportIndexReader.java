/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.EsHelper;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex.ES_TYPE_INDEX_REFERS_TO;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@RequiredArgsConstructor
@Component
@Slf4j
public class TimestampBasedImportIndexReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public Optional<TimestampBasedImportIndexDto> getImportIndex(String typeIndexComesFrom, String engineAlias) {
    log.debug("Fetching timestamp based import index of type '{}'", typeIndexComesFrom);

    GetResponse getResponse = null;
    TimestampBasedImportIndexDto dto;
    try {
      GetRequest getRequest = new GetRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME).id(EsHelper.constructKey(
        typeIndexComesFrom,
        engineAlias
      ));
      getResponse = esClient.get(getRequest);
    } catch (Exception ignored) {
    }

    if (getResponse != null && getResponse.isExists()) {
      String content = getResponse.getSourceAsString();
      try {
        dto = objectMapper.readValue(content, TimestampBasedImportIndexDto.class);
      } catch (IOException e) {
        log.debug("Error while reading timestamp based import index from elastic search!", e);
        return Optional.empty();
      }
    } else {
      log.debug(
        "Was not able to retrieve timestamp based import index for type [{}] and engine [{}] from elasticsearch.",
        typeIndexComesFrom,
        engineAlias
      );
      return Optional.empty();
    }
    return Optional.of(dto);
  }

  public List<TimestampBasedImportIndexDto> getAllImportIndicesForTypes(List<String> indexTypes) {
    log.debug("Fetching timestamp based import indices of types '{}'", indexTypes);

    final SearchRequest searchRequest = new SearchRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
      .source(new SearchSourceBuilder()
                .query(termsQuery(ES_TYPE_INDEX_REFERS_TO, indexTypes))
                .size(LIST_FETCH_LIMIT));

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      log.error("Was not able to get timestamp based import indices!", e);
      throw new OptimizeRuntimeException("Was not able to get timestamp based import indices!", e);
    }
    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), TimestampBasedImportIndexDto.class, objectMapper);
  }

}
