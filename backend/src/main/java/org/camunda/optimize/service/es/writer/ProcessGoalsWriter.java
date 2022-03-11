/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.goals.ProcessGoalsDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_GOALS_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessGoalsWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void createProcessGoals(ProcessGoalsDto processGoalsDto) {
    try {
      IndexRequest request = new IndexRequest(PROCESS_GOALS_INDEX_NAME)
        .id(processGoalsDto.getProcessDefinitionKey())
        .source(objectMapper.writeValueAsString(processGoalsDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);
      esClient.index(request);
    } catch (IOException e) {
      final String errorMessage = String.format(
        "There was a problem while writing the process goals: [%s].",
        processGoalsDto
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

}
