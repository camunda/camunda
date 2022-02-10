/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.process;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.ProcessGoalDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class ProcessGoalsIT extends AbstractIT {

  private static final String FIRST_PROCESS_DEFINITION_KEY = "firstProcessDefinition";
  private static final String SECOND_PROCESS_DEFINITION_KEY = "secondProcessDefinition";
  private static final String STARTED_EVENT = "startedEvent";
  private static final String FINISHED_EVENT = "finishedEvent";
  private static final String EXTERNAL_EVENT_GROUP = "testGroup";
  private static final String EXTERNAL_EVENT_SOURCE = "integrationTestSource";
  private static final String BPMN_START_EVENT_ID = "StartEvent_1";
  private static final String USER_TASK_ID_ONE = "user_task_1";
  private static final String BPMN_END_EVENT_ID = "EndEvent_1";
  private static final String EVENT_PROCESS_NAME = "myEventProcess";
  private static final String MY_TRACE_ID_1 = "myTraceId1";
  private static final String VARIABLE_ID = "var";
  private static final String VARIABLE_VALUE = "value";

  @Test
  public void getProcessGoals_notPossibleForUnauthenticatedUser() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessDefinitionGoalsRequest()
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getProcessGoals_noProcessDefinitionGoalsFound() {
    // when
    List<ProcessGoalDto> processGoalDtos = getProcessGoals();

    // then
    assertThat(processGoalDtos).isEmpty();
  }

  @Test
  public void getProcessGoals_processDefinitionGoalsFetchedSuccesfully() {
    // given
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    deploySimpleProcessDefinition(SECOND_PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessGoalDto> processGoalDtos = getProcessGoals();

    // then
    assertThat(processGoalDtos).hasSize(2).containsExactlyInAnyOrder(
      new ProcessGoalDto(
        FIRST_PROCESS_DEFINITION_KEY,
        FIRST_PROCESS_DEFINITION_KEY,
        Collections.emptyList(),
        null
      ),
      new ProcessGoalDto(
        SECOND_PROCESS_DEFINITION_KEY,
        SECOND_PROCESS_DEFINITION_KEY,
        Collections.emptyList(),
        null
      )
    );
  }

  @Test
  public void getProcessGoals_userCanOnlySeeAuthorizedProcesses() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    deploySimpleProcessDefinition(SECOND_PROCESS_DEFINITION_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessGoalDto> processGoalDtos = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessDefinitionGoalsRequest()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .executeAndReturnList(ProcessGoalDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(processGoalDtos).isEmpty();
  }

  @Test
  public void getProcessGoals_processesIncludeAnEventBasedProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition(FIRST_PROCESS_DEFINITION_KEY);
    String eventProcessDefinitionKey = deployEventBasedProcessDefinition();

    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessGoalDto> processGoalDtos = getProcessGoals();

    // then
    assertThat(processGoalDtos).hasSize(2).containsExactlyInAnyOrder(
      new ProcessGoalDto(
        eventProcessDefinitionKey,
        EVENT_PROCESS_NAME,
        Collections.emptyList(),
        null
      ),
      new ProcessGoalDto(
        processDefinition.getKey(),
        processDefinition.getName(),
        Collections.emptyList(),
        null
      )
    );
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(processDefinitionKey));
  }

  private EventProcessMappingDto buildSimpleEventProcessMappingDto() {
    return buildSimpleEventProcessMappingDto(
      EventMappingDto.builder()
        .end(EventTypeDto.builder()
               .group(EXTERNAL_EVENT_GROUP)
               .source(EXTERNAL_EVENT_SOURCE)
               .eventName(STARTED_EVENT)
               .build())
        .build(),
      EventMappingDto.builder()
        .end(EventTypeDto.builder()
               .group(EXTERNAL_EVENT_GROUP)
               .source(EXTERNAL_EVENT_SOURCE)
               .eventName(FINISHED_EVENT)
               .build())
        .build()
    );
  }

  private EventProcessMappingDto buildSimpleEventProcessMappingDto(final EventMappingDto startEventMapping,
                                                                   final EventMappingDto endEventMapping) {
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, startEventMapping);
    eventMappings.put(BPMN_END_EVENT_ID, endEventMapping);
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      eventMappings, EVENT_PROCESS_NAME, createTwoEventAndOneTaskActivitiesProcessDefinitionXml()
    );
  }

  @SneakyThrows
  private static String createTwoEventAndOneTaskActivitiesProcessDefinitionXml() {
    return convertBpmnModelToXmlString(getSingleUserTaskDiagram(
      "aProcessName",
      BPMN_START_EVENT_ID,
      BPMN_END_EVENT_ID,
      USER_TASK_ID_ONE
    ));
  }

  @SneakyThrows
  private void executeImportCycle() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler()
      .runImportRound(true)
      .get(10, TimeUnit.SECONDS);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private static String convertBpmnModelToXmlString(final BpmnModelInstance bpmnModel) {
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, bpmnModel);
    return xmlOutput.toString(StandardCharsets.UTF_8);
  }

  private void publishMappingAndExecuteImport(final String eventProcessId) {
    eventProcessClient.publishEventProcessMapping(eventProcessId);
    // we execute the import cycle so the event instance index gets created
    executeImportCycle();
  }

  private void ingestTestEvent(final String eventId,
                               final String eventName,
                               final OffsetDateTime eventTimestamp) {
    embeddedOptimizeExtension.getEventService()
      .saveEventBatch(
        Collections.singletonList(
          EventDto.builder()
            .id(eventId)
            .eventName(eventName)
            .timestamp(eventTimestamp.toInstant().toEpochMilli())
            .traceId(MY_TRACE_ID_1)
            .group(EXTERNAL_EVENT_GROUP)
            .source(EXTERNAL_EVENT_SOURCE)
            .data(ImmutableMap.of(VARIABLE_ID, VARIABLE_VALUE))
            .build()
        )
      );
  }

  private List<ProcessGoalDto> getProcessGoals() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetProcessDefinitionGoalsRequest()
      .executeAndReturnList(ProcessGoalDto.class, Response.Status.OK.getStatusCode());
  }

  private String deployEventBasedProcessDefinition() {
    ingestTestEvent(IdGenerator.getNextId(), STARTED_EVENT, OffsetDateTime.now());
    ingestTestEvent(IdGenerator.getNextId(), FINISHED_EVENT, OffsetDateTime.now());
    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto();
    String eventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);
    publishMappingAndExecuteImport(eventProcessDefinitionKey);
    executeImportCycle();
    return eventProcessDefinitionKey;
  }
}
