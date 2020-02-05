/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
public class VariableUpdateInstanceWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void importVariableUpdatesToVariableUpdateInstances(List<ProcessVariableDto> variableUpdates) {
    String importItemName = "variable instances";
    log.debug("Writing [{}] {} to ES.", variableUpdates.size(), importItemName);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      variableUpdates,
      this::addVariableUpdateToVariableUpdatesInstances
    );
  }

  private void addVariableUpdateToVariableUpdatesInstances(BulkRequest addVariableUpdateInstancesBulkRequest,
                                                           ProcessVariableDto variableUpdate) {
    VariableUpdateInstanceDto variableUpdateInstanceDto = VariableUpdateInstanceDto.builder()
      .instanceId(variableUpdate.getId())
      .name(variableUpdate.getName())
      .type(variableUpdate.getType())
      .value(variableUpdate.getValue())
      .processInstanceId(variableUpdate.getProcessInstanceId())
      .tenantId(variableUpdate.getTenantId())
      .timestamp(variableUpdate.getTimestamp())
      .build();

    try {
      final IndexRequest request = new IndexRequest(VARIABLE_UPDATE_INSTANCE_INDEX_NAME)
        .id(IdGenerator.getNextId())
        .source(objectMapper.writeValueAsString(variableUpdateInstanceDto), XContentType.JSON);
      addVariableUpdateInstancesBulkRequest.add(request);
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize Variable Instance: {}", variableUpdateInstanceDto, e);
    }
  }

}
