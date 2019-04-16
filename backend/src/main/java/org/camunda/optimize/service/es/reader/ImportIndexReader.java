/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
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
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IMPORT_INDEX_TYPE;

@Component
public class ImportIndexReader {

  private final Logger logger = LoggerFactory.getLogger(ImportIndexReader.class);


  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  @Autowired
  public ImportIndexReader(RestHighLevelClient esClient, ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public Optional<AllEntitiesBasedImportIndexDto> getImportIndex(String id) {
    logger.debug("Fetching import index of type [{}]", id);

    GetRequest getRequest = new GetRequest(
      getOptimizeIndexAliasForType(IMPORT_INDEX_TYPE),
      IMPORT_INDEX_TYPE,
      id
    );

    GetResponse getResponse = null;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (Exception ignored) {    }


    if (getResponse != null && getResponse.isExists()) {
      try {
        AllEntitiesBasedImportIndexDto storedIndex =
          objectMapper.readValue(getResponse.getSourceAsString(), AllEntitiesBasedImportIndexDto.class);
        return Optional.of(storedIndex);
      } catch (IOException e) {
        logger.error("Was not able to retrieve import index of [{}]. Reason: {}", id, e);
        return Optional.empty();
      }
    } else {
      logger.debug("Was not able to retrieve import index for type '{}' from Elasticsearch. " +
        "Desired index does not exist.", id);
      return Optional.empty();
    }
  }

}
