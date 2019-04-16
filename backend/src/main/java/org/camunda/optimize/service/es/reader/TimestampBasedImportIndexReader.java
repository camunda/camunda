/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.util.EsHelper;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;

@Component
public class TimestampBasedImportIndexReader {

  private final Logger logger = LoggerFactory.getLogger(TimestampBasedImportIndexReader.class);

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  @Autowired
  public TimestampBasedImportIndexReader(RestHighLevelClient esClient, ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public Optional<TimestampBasedImportIndexDto> getImportIndex(String typeIndexComesFrom, String engineAlias) {
    logger.debug("Fetching definition based import index of type '{}'", typeIndexComesFrom);

    GetResponse getResponse = null;
    TimestampBasedImportIndexDto dto;
    try {
      GetRequest getRequest = new GetRequest(
        getOptimizeIndexAliasForType(TIMESTAMP_BASED_IMPORT_INDEX_TYPE),
        TIMESTAMP_BASED_IMPORT_INDEX_TYPE,
        EsHelper.constructKey(typeIndexComesFrom, engineAlias)
      );
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (Exception ignored) {}

    if (getResponse != null && getResponse.isExists()) {
      String content = getResponse.getSourceAsString();
      try {
        dto = objectMapper.readValue(content, TimestampBasedImportIndexDto.class);
      } catch (IOException e) {
        logger.debug("Error while reading definition based import index from elastic search!", e);
        return Optional.empty();
      }
    } else {
      logger.debug(
        "Was not able to retrieve definition based import index from [{}] " +
          "for type [{}] and engine [{}] from elasticsearch.",
        getOptimizeIndexAliasForType(TIMESTAMP_BASED_IMPORT_INDEX_TYPE),
        typeIndexComesFrom,
        engineAlias
      );
      return Optional.empty();
    }
    return Optional.of(dto);
  }

}
