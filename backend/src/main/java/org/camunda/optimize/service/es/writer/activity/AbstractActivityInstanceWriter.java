/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@AllArgsConstructor
@Component
public abstract class AbstractActivityInstanceWriter {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public List<ImportRequestDto> generateActivityInstanceImports(List<FlowNodeEventDto> activityInstances) {
    Map<String, List<OptimizeDto>> processInstanceToEvents = new HashMap<>();
    for (FlowNodeEventDto e : activityInstances) {
      if (!processInstanceToEvents.containsKey(e.getProcessInstanceId())) {
        processInstanceToEvents.put(e.getProcessInstanceId(), new ArrayList<>());
      }
      processInstanceToEvents.get(e.getProcessInstanceId()).add(e);
    }

    String importItemName = "activity instances";
    log.debug("Creating imports for {} [{}].", activityInstances.size(), importItemName);

    return processInstanceToEvents.entrySet().stream()
      .map(entry -> ImportRequestDto.builder()
        .importName(importItemName)
        .esClient(esClient)
        .request(createImportRequestForActivityInstance(entry))
        .build())
      .collect(Collectors.toList());
  }

  private UpdateRequest createImportRequestForActivityInstance(Map.Entry<String, List<OptimizeDto>> activitiesByProcessInstance) {
    if (!activitiesByProcessInstance.getValue().stream().allMatch(dto -> dto instanceof FlowNodeEventDto)) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    final List<FlowNodeEventDto> activityInstances =
      (List<FlowNodeEventDto>) (List<?>) activitiesByProcessInstance.getValue();
    final String processInstanceId = activitiesByProcessInstance.getKey();

    final List<FlowNodeInstanceDto> flowNodeInstanceDtos = convertToFlowNodeInstanceDtos(activityInstances);
    final Map<String, Object> params = new HashMap<>();
    // see https://discuss.elastic.co/t/how-to-update-nested-objects-in-elasticsearch-2-2-script-via-java-api/43135

    try {
      params.put(EVENTS, flowNodeInstanceDtos);
      final Script updateScript = createDefaultScriptWithSpecificDtoParams(
        createInlineUpdateScript(),
        params,
        objectMapper
      );

      final ProcessInstanceDto procInst = ProcessInstanceDto.builder()
        .processInstanceId(processInstanceId)
        .engine(activityInstances.get(0).getEngineAlias())
        .events(flowNodeInstanceDtos)
        .build();
      String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);
      return new UpdateRequest()
        .index(PROCESS_INSTANCE_INDEX_NAME)
        .id(processInstanceId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    } catch (IOException e) {
      String reason = String.format(
        "Error while processing JSON for activity instances with ID [%s].",
        processInstanceId
      );
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  protected abstract String createInlineUpdateScript();

  private List<FlowNodeInstanceDto> convertToFlowNodeInstanceDtos(List<FlowNodeEventDto> activityInstances) {
    return activityInstances.stream()
      .map(activity -> FlowNodeInstanceDto.builder()
        .durationInMs(activity.getDurationInMs())
        .activityId(activity.getActivityId())
        .id(activity.getId())
        .activityType(activity.getActivityType())
        .startDate(activity.getStartDate())
        .endDate(activity.getEndDate())
        .processInstanceId(activity.getProcessInstanceId())
        .canceled(activity.getCanceled())
        .build()
      ).collect(Collectors.toList());
  }

}
