/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.bpm.engine.ActivityTypes;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.TracedEventDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.events.CamundaEventService;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.script.Script;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskEndEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskStartEventSuffix;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class CamundaEventTraceStateImportIT extends AbstractEventTraceStateImportIT {

  private static final String START_EVENT = ActivityTypes.START_EVENT;
  private static final String END_EVENT = ActivityTypes.END_EVENT_NONE;
  private static final String USER_TASK = ActivityTypes.TASK_USER_TASK;

  @SneakyThrows
  @Test
  public void noCamundaEventStateTraceIndicesCreatedIfEventBasedProcessesDisabled() {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(false);
    final String definitionKey = "myCamundaProcess";
    deployAndStartUserTaskProcessWithName(definitionKey);
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // when
    processEventCountAndTraces();

    // then
    assertThat(
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
        .exists(new GetIndexRequest(getTraceStateIndexNameForDefinitionKey(definitionKey)))).isFalse();
    assertThat(
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
        .exists(new GetIndexRequest(getSequenceCountIndexNameForDefinitionKey(definitionKey)))
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
    importAllEngineEntitiesFromScratch();

    // when
    processEventCountAndTraces();

    // then
    assertTracesAndCountsArePresentForDefinitionKey(definitionKey1, processInstanceEngineDto1, true);
    assertTracesAndCountsArePresentForDefinitionKey(definitionKey2, processInstanceEngineDto2, true);
  }

  @Test
  public void eventTracesAndCountsAreCreatedCorrectlyForProcessWithEventsWithIdenticalTimestamps() {
    // given
    final String definitionKey = "myCamundaProcess1";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcessWithName(definitionKey);
    engineIntegrationExtension.finishAllRunningUserTasks();
    OffsetDateTime eventTimestamp = OffsetDateTime.now().withNano(0);
    engineDatabaseExtension.changeProcessInstanceStartDates(ImmutableMap.of(
      processInstanceEngineDto.getId(),
      eventTimestamp
    ));
    engineDatabaseExtension.changeProcessInstanceEndDates(ImmutableMap.of(
      processInstanceEngineDto.getId(),
      eventTimestamp
    ));
    engineDatabaseExtension.changeAllFlowNodeStartDates(ImmutableMap.of(
      processInstanceEngineDto.getId(),
      eventTimestamp
    ));
    engineDatabaseExtension.changeAllFlowNodeEndDates(ImmutableMap.of(
      processInstanceEngineDto.getId(),
      eventTimestamp
    ));

    importAllEngineEntitiesFromScratch();

    // when
    processEventCountAndTraces();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllStoredCamundaActivityEventsForDefinition(definitionKey))
      .extracting(CamundaActivityEventDto::getTimestamp)
      .allMatch(offsetDateTime -> offsetDateTime.equals(eventTimestamp));
    assertTracesAndCountsArePresentForDefinitionKey(definitionKey, processInstanceEngineDto, true);
  }

  @Test
  public void eventTracesAndCountsAreCreatedCorrectlyForProcessWithEventsWithIdenticalTimestamps_noOrderCountersFromEngine() {
    // given
    final String definitionKey = "myCamundaProcess1";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcessWithName(definitionKey);
    engineIntegrationExtension.finishAllRunningUserTasks();
    OffsetDateTime eventTimestamp = OffsetDateTime.now().withNano(0);
    engineDatabaseExtension.changeProcessInstanceStartDates(ImmutableMap.of(
      processInstanceEngineDto.getId(),
      eventTimestamp
    ));
    engineDatabaseExtension.changeProcessInstanceEndDates(ImmutableMap.of(
      processInstanceEngineDto.getId(),
      eventTimestamp
    ));
    engineDatabaseExtension.changeAllFlowNodeStartDates(ImmutableMap.of(
      processInstanceEngineDto.getId(),
      eventTimestamp
    ));
    engineDatabaseExtension.changeAllFlowNodeEndDates(ImmutableMap.of(
      processInstanceEngineDto.getId(),
      eventTimestamp
    ));

    importAllEngineEntitiesFromScratch();
    removeStoredOrderCountersForDefinitionKey(definitionKey);

    // when
    processEventCountAndTraces();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllStoredCamundaActivityEventsForDefinition(definitionKey))
      .extracting(CamundaActivityEventDto::getTimestamp)
      .allMatch(offsetDateTime -> offsetDateTime.equals(eventTimestamp));
    assertTracesAndCountsArePresentForDefinitionKey(definitionKey, processInstanceEngineDto, false);
  }

  @Test
  public void eventTracesAndCountsAreCreatedCorrectlyForReimportedEvents_idempotentTraceStates() {
    // given
    final String definitionKey = "myCamundaProcess";
    final ProcessInstanceEngineDto processInstanceEngineDto1 = deployAndStartUserTaskProcessWithName(definitionKey);
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // when
    processEventCountAndTraces();

    // then
    assertTracesAndCountsArePresentForDefinitionKey(definitionKey, processInstanceEngineDto1, true);
    final List<EventTraceStateDto> initialStoredTraceStates = getAllStoredCamundaEventTraceStatesForDefinitionKey(
      definitionKey);
    assertThat(getAllStoredCamundaEventsForEventDefinitionKey(definitionKey)).hasSize(6);

    // when import index reset and traces reprocessed after event reimport
    deleteTraceStateImportIndexForDefinitionKey(definitionKey);
    importAllEngineEntitiesFromScratch();
    processEventCountAndTraces();

    // then traces are the same as after first process
    assertTracesAndCountsArePresentForDefinitionKey(definitionKey, processInstanceEngineDto1, true);
    assertThat(initialStoredTraceStates)
      .containsExactlyElementsOf(getAllStoredCamundaEventTraceStatesForDefinitionKey(definitionKey));
    assertThat(getLastProcessedEntityTimestampFromElasticsearch(definitionKey))
      .isEqualTo(findMostRecentEventTimestamp(definitionKey));
    assertThat(getAllStoredCamundaEventsForEventDefinitionKey(definitionKey)).hasSize(12);
  }

  @SneakyThrows
  private void deleteTraceStateImportIndexForDefinitionKey(final String definitionKey) {
    DeleteByQueryRequest request = new DeleteByQueryRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME);
    request.setRefresh(true);
    request.setQuery(boolQuery().must(termQuery(
      TimestampBasedImportIndexDto.Fields.esTypeIndexRefersTo,
      EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + definitionKey.toLowerCase()
    )));
    elasticSearchIntegrationTestExtension.getOptimizeElasticClient().deleteByQuery(request);
  }

  private void removeStoredOrderCountersForDefinitionKey(final String definitionKey) {
    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient(),
      String.format("Camunda activity events with definitionKey [%s]", definitionKey),
      new Script("ctx._source.orderCounter = null"),
      matchAllQuery(),
      CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + definitionKey
    );
  }

  private void assertTracesAndCountsArePresentForDefinitionKey(final String definitionKey,
                                                               final ProcessInstanceEngineDto processInstanceEngineDto,
                                                               final boolean useOrderCounters) {
    assertThat(getAllStoredCamundaEventTraceStatesForDefinitionKey(definitionKey))
      .hasSize(1)
      .allSatisfy(eventTraceStateDto -> {
        assertThat(eventTraceStateDto.getTraceId()).isEqualTo(processInstanceEngineDto.getId());
        assertThat(eventTraceStateDto.getEventTrace())
          .hasSize(4)
          .allSatisfy(tracedEventDto -> {
            assertThat(tracedEventDto.getGroup()).isEqualTo(definitionKey);
            assertThat(tracedEventDto.getSource()).isEqualTo("camunda");
            assertThat(tracedEventDto.getTimestamp()).isNotNull();
            assertThat(useOrderCounters == (tracedEventDto.getOrderCounter() != null));
          })
          .extracting(TracedEventDto::getEventName)
          .containsExactlyInAnyOrder(
            START_EVENT,
            applyCamundaTaskStartEventSuffix(USER_TASK),
            applyCamundaTaskEndEventSuffix(USER_TASK),
            END_EVENT
          );
        if (useOrderCounters) {
          assertThat(eventTraceStateDto.getEventTrace())
            .isSortedAccordingTo(Comparator.comparing(TracedEventDto::getTimestamp)
                                   .thenComparing(TracedEventDto::getOrderCounter));
        } else {
          assertThat(eventTraceStateDto.getEventTrace())
            .isSortedAccordingTo(Comparator.comparing(TracedEventDto::getTimestamp));
        }
      });
    assertThat(
      getAllStoredEventSequenceCountsForDefinitionKey(definitionKey)
        .stream()
        .map(EventSequenceCountDto::getCount)
        .mapToLong(value -> value)
        .sum()
    ).isEqualTo(4L);

    assertThat(getLastProcessedEntityTimestampFromElasticsearch(definitionKey))
      .isEqualTo(findMostRecentEventTimestamp(definitionKey));
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcessWithName(String processName) {
    return engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram(
      processName,
      START_EVENT,
      END_EVENT,
      USER_TASK
    ));
  }

  private Long findMostRecentEventTimestamp(final String definitionKey) {
    return getAllStoredCamundaEventsForEventDefinitionKey(definitionKey).stream()
      .filter(this::isStateTraceable)
      .map(CamundaActivityEventDto::getTimestamp)
      .mapToLong(e -> e.toInstant().toEpochMilli())
      .max()
      .getAsLong();
  }

  private boolean isStateTraceable(CamundaActivityEventDto camundaActivityEventDto) {
    return !camundaActivityEventDto.getActivityType().equalsIgnoreCase(CamundaEventService.PROCESS_START_TYPE) &&
      !camundaActivityEventDto.getActivityType().equalsIgnoreCase(CamundaEventService.PROCESS_END_TYPE);
  }

  private List<CamundaActivityEventDto> getAllStoredCamundaEventsForEventDefinitionKey(final String processDefinitionKey) {
    return elasticSearchIntegrationTestExtension.getAllStoredCamundaActivityEventsForDefinition(processDefinitionKey);
  }

  private List<EventTraceStateDto> getAllStoredCamundaEventTraceStatesForDefinitionKey(final String definitionKey) {
    return getAllStoredDocumentsForIndexAsClass(
      getTraceStateIndexNameForDefinitionKey(definitionKey), EventTraceStateDto.class
    );
  }

  private List<EventSequenceCountDto> getAllStoredEventSequenceCountsForDefinitionKey(final String definitionKey) {
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

}
