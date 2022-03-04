/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader.importindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.EsHelper;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@RequiredArgsConstructor
@Component
@Slf4j
public abstract class AbstractImportIndexReader<T extends ImportIndexDto<D>, D extends DataSourceDto> {

  protected final OptimizeElasticsearchClient esClient;
  protected final ObjectMapper objectMapper;

  protected abstract String getImportIndexType();

  protected abstract String getImportIndexName();

  protected abstract Class<T> getImportDTOClass();

  public Optional<T> getImportIndex(String typeIndexComesFrom, D dataSourceDto) {
    log.debug("Fetching {} import index of type '{}'", getImportIndexType(), typeIndexComesFrom);

    GetResponse getResponse = null;
    GetRequest getRequest = new GetRequest(getImportIndexName())
      .id(EsHelper.constructKey(typeIndexComesFrom, dataSourceDto));
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      log.error("Could not fetch {} import index", getImportIndexType(), e);
    }

    if (getResponse != null && getResponse.isExists()) {
      String content = getResponse.getSourceAsString();
      try {
        return Optional.of(objectMapper.readValue(content, getImportDTOClass()));
      } catch (IOException e) {
        log.debug("Error while reading {} import index from elasticsearch!", getImportIndexType(), e);
        return Optional.empty();
      }
    } else {
      log.debug(
        "Was not able to retrieve {} import index for type [{}] and engine [{}] from elasticsearch.",
        getImportIndexType(),
        typeIndexComesFrom,
        dataSourceDto
      );
      return Optional.empty();
    }
  }

}
