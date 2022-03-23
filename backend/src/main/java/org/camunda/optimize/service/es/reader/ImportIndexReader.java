/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IMPORT_INDEX_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class ImportIndexReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public Optional<AllEntitiesBasedImportIndexDto> getImportIndex(String id) {
    log.debug("Fetching import index of type [{}]", id);

    GetRequest getRequest = new GetRequest(IMPORT_INDEX_INDEX_NAME).id(id);

    GetResponse getResponse = null;
    try {
      getResponse = esClient.get(getRequest);
    } catch (Exception ignored) {
      // do nothing
    }

    if (getResponse != null && getResponse.isExists()) {
      try {
        AllEntitiesBasedImportIndexDto storedIndex =
          objectMapper.readValue(getResponse.getSourceAsString(), AllEntitiesBasedImportIndexDto.class);
        return Optional.of(storedIndex);
      } catch (IOException e) {
        log.error("Was not able to retrieve import index of [{}]. Reason: {}", id, e);
        return Optional.empty();
      }
    } else {
      log.debug("Was not able to retrieve import index for type '{}' from Elasticsearch. " +
                  "Desired index does not exist.", id);
      return Optional.empty();
    }
  }

}
