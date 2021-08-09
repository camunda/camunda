/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.EsHelper;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.POSITION_BASED_IMPORT_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
public class PositionBasedImportIndexReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public Optional<PositionBasedImportIndexDto> getImportIndex(String typeIndexComesFrom, DataSourceDto dataSourceDto) {
    log.debug("Fetching position based import index of type '{}'", typeIndexComesFrom);

    GetResponse getResponse = null;
    GetRequest getRequest = new GetRequest(POSITION_BASED_IMPORT_INDEX_NAME)
      .id(EsHelper.constructKey(typeIndexComesFrom, dataSourceDto));
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      log.error("Could not fetch position based import index", e);
    }

    if (getResponse != null && getResponse.isExists()) {
      String content = getResponse.getSourceAsString();
      try {
        return Optional.of(objectMapper.readValue(content, PositionBasedImportIndexDto.class));
      } catch (IOException e) {
        log.debug("Error while reading position based import index from elasticsearch!", e);
        return Optional.empty();
      }
    } else {
      log.debug(
        "Was not able to retrieve position based import index for type [{}] and engine [{}] from elasticsearch.",
        typeIndexComesFrom,
        dataSourceDto
      );
      return Optional.empty();
    }
  }

}
