/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.reader.importindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.service.db.reader.importindex.AbstractImportIndexReader;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public abstract class AbstractImportIndexReaderES<T extends ImportIndexDto<D>, D extends DataSourceDto> implements AbstractImportIndexReader<T, D> {

  protected final OptimizeElasticsearchClient esClient;
  protected final ObjectMapper objectMapper;

  @Override
  public Optional<T> getImportIndex(String typeIndexComesFrom, D dataSourceDto) {
    log.debug("Fetching {} import index of type '{}'", getImportIndexType(), typeIndexComesFrom);

    GetResponse getResponse = null;
    GetRequest getRequest = new GetRequest(getImportIndexName())
      .id(DatabaseHelper.constructKey(typeIndexComesFrom, dataSourceDto));
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
