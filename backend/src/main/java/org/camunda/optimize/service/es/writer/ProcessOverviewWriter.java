/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.ProcessOverviewDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.update.UpdateRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessOverviewWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void updateProcess(final ProcessOverviewDto processOverviewDto) {
    try {
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(PROCESS_OVERVIEW_INDEX_NAME)
        .id(processOverviewDto.getProcessDefinitionKey())
        .script(ElasticsearchWriterUtil.createFieldUpdateScript(
          Set.of(ProcessOverviewDto.Fields.owner, ProcessOverviewDto.Fields.processDefinitionKey),
          processOverviewDto,
          objectMapper
        ))
        .upsert(objectMapper.convertValue(processOverviewDto, Map.class))
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (IOException e) {
      final String errorMessage = String.format(
        "There was a problem while writing the process: [%s].",
        processOverviewDto
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }
}
