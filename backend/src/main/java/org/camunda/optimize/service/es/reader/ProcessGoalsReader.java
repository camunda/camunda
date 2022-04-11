/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_GOALS_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessGoalsReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public Map<String, ProcessGoalsDto> getGoalsForProcessesByKey(Set<String> processDefinitionKeys) {
    log.debug("Fetching process goals for [{}] processes", processDefinitionKeys.size());
    if (processDefinitionKeys.isEmpty()) {
      return Collections.emptyMap();
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder
      .query(QueryBuilders.idsQuery().addIds(processDefinitionKeys.toArray(new String[0])))
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(PROCESS_GOALS_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch goals for processes [%s]", processDefinitionKeys);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), ProcessGoalsDto.class, objectMapper)
      .stream()
      .collect(Collectors.toMap(ProcessGoalsDto::getProcessDefinitionKey, Function.identity()));
  }

}
