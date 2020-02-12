/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import lombok.SneakyThrows;
import org.camunda.bpm.engine.ActivityTypes;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.dto.optimize.query.event.TracedEventDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.reader.ElasticsearchHelper.mapHits;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaProcessInstanceEndEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaProcessInstanceStartEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaTaskEndEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaTaskStartEventSuffix;

public class CamundaEventTraceStateImportIT extends AbstractIT {

  private static final String START_EVENT = ActivityTypes.START_EVENT;
  private static final String END_EVENT = ActivityTypes.END_EVENT_NONE;
  private static final String USER_TASK = ActivityTypes.TASK_USER_TASK;

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(true);
  }

  @SneakyThrows
  @Test
  public void noCamundaEventStateTraceIndicesCreatedIfEventBasedProcessesDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(false);
    final String definitionKey = "myCamundaProcess";
    deployAndStartUserTaskProcessWithName(definitionKey);
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    processEventCountAndTraces();

    // then
    assertThat(
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
        .exists(new GetIndexRequest(getTraceStateIndexNameForDefinitionKey(definitionKey)), RequestOptions.DEFAULT)
    ).isFalse();
    assertThat(
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
        .exists(
          new GetIndexRequest(getSequenceCountIndexNameForDefinitionKey(definitionKey)), RequestOptions.DEFAULT
        )
    ).isFalse();
  }

  @Test
  public void expectedEventTracesAndCountsAreCreatedForMultipleCamundaProcesses() {
    // given
    final String definitionKey1 = "myCamundaProcess1";
    final String definitionKey2 = "myCamundaProcess2";
    final ProcessInstanceEngineDto processInstanceEngineDto1 = deployAndStartUserTaskProcessWithName(definitionKey1);
    final ProcessInstanceEngineDto processInstanceEngineDto2 = deployAndStartUserTaskProcessWithName(definitionKey2);
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    processEventCountAndTraces();

    // then
    assertTracesAndCountsArePresentForDefinitionKey(definitionKey1, processInstanceEngineDto1);
    assertTracesAndCountsArePresentForDefinitionKey(definitionKey2, processInstanceEngineDto2);
  }

  private void assertTracesAndCountsArePresentForDefinitionKey(final String definitionKey,
                                                               final ProcessInstanceEngineDto processInstanceEngineDto) {
    assertThat(getAllStoredCamundaEventTraceStatesForDefinitionKey(definitionKey))
      .hasSize(1)
      .allSatisfy(eventTraceStateDto -> {
        assertThat(eventTraceStateDto.getTraceId()).isEqualTo(processInstanceEngineDto.getId());
        assertThat(eventTraceStateDto.getEventTrace())
          .hasSize(6)
          .allSatisfy(tracedEventDto -> {
            assertThat(tracedEventDto.getGroup()).isEqualTo(definitionKey);
            assertThat(tracedEventDto.getSource()).isEqualTo("camunda");
            assertThat(tracedEventDto.getTimestamp()).isNotNull();
          })
          .extracting(TracedEventDto::getEventName)
          .containsExactlyInAnyOrder(
            applyCamundaProcessInstanceStartEventSuffix(definitionKey),
            START_EVENT,
            applyCamundaTaskStartEventSuffix(USER_TASK),
            applyCamundaTaskEndEventSuffix(USER_TASK),
            END_EVENT,
            applyCamundaProcessInstanceEndEventSuffix(definitionKey)
          )
        ;
      });
    assertThat(
      getAllStoredExternalEventSequenceCountsForDefinitionKey(definitionKey)
        .stream()
        .map(EventSequenceCountDto::getCount)
        .mapToLong(value -> value)
        .sum()
    ).isEqualTo(6L);

    assertThat(getLastProcessedEntityTimestampForProcessDefinitionKey(definitionKey))
      .isEqualTo(findMostRecentEventTimestamp(definitionKey));
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcessWithName(String processName) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(processName)
      .startEvent(START_EVENT)
      .userTask(USER_TASK)
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }

  @SneakyThrows
  private Long getLastProcessedEntityTimestampForProcessDefinitionKey(final String processDefinitionKey) {
    return elasticSearchIntegrationTestExtension
      .getLastProcessedEventTimestampForEventIndexSuffix(processDefinitionKey)
      .toInstant()
      .toEpochMilli();
  }

  private void processEventCountAndTraces() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.processEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private Long findMostRecentEventTimestamp(final String definitionKey) {
    return getAllStoredCamundaEventsForEventDefinitionKey(definitionKey).stream()
      .map(CamundaActivityEventDto::getTimestamp)
      .mapToLong(e -> e.toInstant().toEpochMilli())
      .max()
      .getAsLong();
  }

  public List<CamundaActivityEventDto> getAllStoredCamundaEventsForEventDefinitionKey(final String processDefinitionKey) {
    return elasticSearchIntegrationTestExtension.getAllStoredCamundaActivityEvents(processDefinitionKey);
  }

  public List<EventTraceStateDto> getAllStoredCamundaEventTraceStatesForDefinitionKey(final String definitionKey) {
    return getAllStoredDocumentsForIndexAsClass(
      getTraceStateIndexNameForDefinitionKey(definitionKey), EventTraceStateDto.class
    );
  }

  public List<EventSequenceCountDto> getAllStoredExternalEventSequenceCountsForDefinitionKey(final String definitionKey) {
    return getAllStoredDocumentsForIndexAsClass(
      getSequenceCountIndexNameForDefinitionKey(definitionKey),
      EventSequenceCountDto.class
    );
  }

  private String getSequenceCountIndexNameForDefinitionKey(final String definitionKey) {
    return new EventSequenceCountIndex(definitionKey).getIndexName();
  }

  private String getTraceStateIndexNameForDefinitionKey(final String definitionKey) {
    return new EventTraceStateIndex(definitionKey).getIndexName();
  }

  private <T> List<T> getAllStoredDocumentsForIndexAsClass(final String indexName, final Class<T> dtoClass) {
    SearchResponse response = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(indexName);
    return mapHits(response.getHits(), dtoClass, elasticSearchIntegrationTestExtension.getObjectMapper());
  }

}
