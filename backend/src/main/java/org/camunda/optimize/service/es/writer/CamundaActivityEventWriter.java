/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaActivityEventWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void importActivityInstancesToCamundaActivityEvents(List<CamundaActivityEventDto> camundaActivityEvents) {
    String importItemName = "camunda activity events";
    log.debug("Writing [{}] {} to ES.", camundaActivityEvents.size(), importItemName);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      camundaActivityEvents,
      this::addActivityInstancesToCamundaActivityEvents
    );
  }

  private void addActivityInstancesToCamundaActivityEvents(BulkRequest addCompletedActivityInstancesBulkRequest,
                                                           CamundaActivityEventDto camundaActivityEventDto) {
    try {
      final IndexRequest request = new IndexRequest(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX
                                                      + camundaActivityEventDto.getProcessDefinitionKey())
        .id(IdGenerator.getNextId())
        .source(objectMapper.writeValueAsString(camundaActivityEventDto), XContentType.JSON);
      addCompletedActivityInstancesBulkRequest.add(request);
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize Camunda Activity Event: {}", camundaActivityEventDto, e);
    }
  }

}
