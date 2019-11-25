/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.SimpleEventDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
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

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@AllArgsConstructor
@Component
public abstract class AbstractActivityInstanceWriter {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void importActivityInstances(List<FlowNodeEventDto> events) {
    String importItemName = "activity instances";
    log.debug("Writing [{}] {} to ES.", events.size(), importItemName);

    Map<String, List<OptimizeDto>> processInstanceToEvents = new HashMap<>();
    for (FlowNodeEventDto e : events) {
      if (!processInstanceToEvents.containsKey(e.getProcessInstanceId())) {
        processInstanceToEvents.put(e.getProcessInstanceId(), new ArrayList<>());
      }
      processInstanceToEvents.get(e.getProcessInstanceId()).add(e);
    }

    ElasticsearchWriterUtil.doBulkRequestWithMap(
      esClient,
      importItemName,
      processInstanceToEvents,
      this::addActivityInstancesToProcessInstanceRequest
    );
  }

  private void addActivityInstancesToProcessInstanceRequest(BulkRequest addEventToProcessInstanceBulkRequest,
                                                            Map.Entry<String, List<OptimizeDto>> activityInstanceEntry) {
    if (!activityInstanceEntry.getValue().stream().allMatch(dto -> dto instanceof FlowNodeEventDto)) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    final List<FlowNodeEventDto> activityInstances =
      (List<FlowNodeEventDto>) (List<?>) activityInstanceEntry.getValue();
    final String activityInstanceId = activityInstanceEntry.getKey();

    final List<SimpleEventDto> simpleEvents = getSimpleEventDtos(activityInstances);
    final Map<String, Object> params = new HashMap<>();
    // see https://discuss.elastic.co/t/how-to-update-nested-objects-in-elasticsearch-2-2-script-via-java-api/43135

    try {
      List jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(simpleEvents), List.class);
      params.put(EVENTS, jsonMap);
      final Script updateScript = createDefaultScript(createInlineUpdateScript(), params);

      final FlowNodeEventDto e = getFirst(activityInstances);
      final ProcessInstanceDto procInst = new ProcessInstanceDto()
        .setProcessInstanceId(e.getProcessInstanceId())
        .setEngine(e.getEngineAlias())
        .setEvents(simpleEvents);
      String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);
      final UpdateRequest request = new UpdateRequest()
        .index(PROCESS_INSTANCE_INDEX_NAME)
        .id(activityInstanceId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      addEventToProcessInstanceBulkRequest.add(request);
    } catch (IOException e) {
      String reason = String.format(
        "Error while processing JSON for activity instances with ID [%s].",
        activityInstanceId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  protected abstract String createInlineUpdateScript();

  private FlowNodeEventDto getFirst(List<FlowNodeEventDto> processEvents) {
    return processEvents.get(0);
  }

  private List<SimpleEventDto> getSimpleEventDtos(List<FlowNodeEventDto> processEvents) {
    List<SimpleEventDto> simpleEvents = new ArrayList<>();
    for (FlowNodeEventDto e : processEvents) {
      SimpleEventDto simpleEventDto = new SimpleEventDto();
      simpleEventDto.setDurationInMs(e.getDurationInMs());
      simpleEventDto.setActivityId(e.getActivityId());
      simpleEventDto.setId(e.getId());
      simpleEventDto.setActivityType(e.getActivityType());
      simpleEventDto.setStartDate(e.getStartDate());
      simpleEventDto.setEndDate(e.getEndDate());
      simpleEvents.add(simpleEventDto);
    }
    return simpleEvents;
  }

}