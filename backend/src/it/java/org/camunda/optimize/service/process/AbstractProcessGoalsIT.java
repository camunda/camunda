/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.process;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalDto;
import org.camunda.optimize.service.util.IdGenerator;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT.EXTERNAL_EVENT_GROUP;
import static org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT.EXTERNAL_EVENT_SOURCE;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public abstract class AbstractProcessGoalsIT extends AbstractIT {

  protected static final String FIRST_PROCESS_DEFINITION_KEY = "firstProcessDefinition";
  protected static final String SECOND_PROCESS_DEFINITION_KEY = "secondProcessDefinition";

  protected void setGoalsForProcess(final String defKey, final List<ProcessDurationGoalDto> goals) {
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(defKey, goals)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  protected EventProcessDefinitionDto deployEventBasedProcessDefinition() {
    ingestTestEvent(IdGenerator.getNextId(), START_EVENT, OffsetDateTime.now());
    ingestTestEvent(IdGenerator.getNextId(), END_EVENT, OffsetDateTime.now());
    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto();
    String eventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);
    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey);
    // we execute the import cycle so the event instance index gets created
    executeImportCycle();
    executeImportCycle();
    final EventProcessDefinitionDto eventProcessDefinitionDto = new EventProcessDefinitionDto();
    eventProcessDefinitionDto.setName(simpleEventProcessMappingDto.getName());
    eventProcessDefinitionDto.setKey(eventProcessDefinitionKey);
    return eventProcessDefinitionDto;
  }

  private EventProcessMappingDto buildSimpleEventProcessMappingDto() {
    return buildSimpleEventProcessMappingDto(
      buildEventMappingDto(START_EVENT),
      buildEventMappingDto(END_EVENT)
    );
  }

  private EventMappingDto buildEventMappingDto(final String endEvent) {
    return EventMappingDto.builder()
      .end(EventTypeDto.builder()
             .group(EXTERNAL_EVENT_GROUP)
             .source(EXTERNAL_EVENT_SOURCE)
             .eventName(endEvent)
             .build())
      .build();
  }

  private EventProcessMappingDto buildSimpleEventProcessMappingDto(final EventMappingDto startEventMapping,
                                                                   final EventMappingDto endEventMapping) {
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(START_EVENT, startEventMapping);
    eventMappings.put(END_EVENT, endEventMapping);
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      eventMappings, "myEventProcess", createTwoEventAndOneTaskActivitiesProcessDefinitionXml()
    );
  }

  @SneakyThrows
  protected void executeImportCycle() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler()
      .runImportRound(true)
      .get(10, TimeUnit.SECONDS);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected void ingestTestEvent(final String eventId,
                                 final String eventName,
                                 final OffsetDateTime eventTimestamp) {
    embeddedOptimizeExtension.getEventService()
      .saveEventBatch(
        Collections.singletonList(
          EventDto.builder()
            .id(eventId)
            .eventName(eventName)
            .timestamp(eventTimestamp.toInstant().toEpochMilli())
            .traceId("myTraceId1")
            .group(EXTERNAL_EVENT_GROUP)
            .source(EXTERNAL_EVENT_SOURCE)
            .build()
        )
      );
  }

  @SneakyThrows
  private static String createTwoEventAndOneTaskActivitiesProcessDefinitionXml() {
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, getSingleUserTaskDiagram("aProcessName"));
    return xmlOutput.toString(StandardCharsets.UTF_8);
  }

}
