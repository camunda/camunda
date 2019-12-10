/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.SimpleEventDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class EventProcessInstanceImportIT extends AbstractIT {

  private static final String MY_TRACE_ID_1 = "myTraceId1";
  private static final String EVENT_GROUP = "test";
  private static final String EVENT_SOURCE = "integrationTest";
  private static final String BPMN_START_EVENT_ID = "StartEvent_1";
  private static final String BPMN_END_EVENT_ID = "EndEvent_1";
  private static final String VARIABLE_ID = "var";
  private static final String VARIABLE_VALUE = "value";

  @Test
  public void dedicatedInstanceIndexIsCreatedForPublishedEventProcess() {
    // given
    final String eventProcessId = createSimpleEventProcessMapping("whatever", "huh");

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final Optional<EventProcessPublishStateDto> eventProcessPublishState =
      getEventProcessPublishStateDtoFromElasticsearch(eventProcessId);
    assertThat(eventProcessPublishState).isNotEmpty();

    final List<String> eventProcessInstanceIndices = getEventProcessInstanceIndicesFromElasticsearch();
    assertThat(eventProcessInstanceIndices)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(indexName -> assertThat(indexName).contains(eventProcessPublishState.get().getId()));
  }

  @Test
  public void dedicatedInstanceIndexIsDeletedOnCancelPublish() {
    // given
    final String eventProcessId = createSimpleEventProcessMapping("whatever", "huh");

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    eventProcessClient.cancelPublishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final List<String> eventProcessInstanceIndices = getEventProcessInstanceIndicesFromElasticsearch();
    assertThat(eventProcessInstanceIndices).hasSize(0);
  }

  @Test
  public void instancesAreGeneratedForExistingEventsAfterPublish() {
    // given
    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventName = "startedEvent";
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventName = "finishedEvent";
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, endDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedProcessInstanceForTraceId(eventProcessId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build(),
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(endDateTime)
                      .endDate(endDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );

        }
      );
  }

  @Test
  public void instancesAreGeneratedForExistingEventsAfterPublish_otherEventsHaveNoEffect() {
    // given
    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventName = "startedEvent";
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventName = "finishedEvent";
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, endDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    ingestTestEvent("randomOtherEvent", LocalDateUtil.getCurrentDateTime());
    ingestTestEvent("evenAnotherEvent", LocalDateUtil.getCurrentDateTime());

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedProcessInstanceForTraceId(eventProcessId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build(),
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(endDateTime)
                      .endDate(endDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );

        }
      );
  }

  @Test
  public void instancesAreGeneratedWhenEventsAreIngestedAfterPublish() {
    // given
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final String ingestedStartEventName = "startedEvent";
    final String ingestedEndEventName = "finishedEvent";

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, endDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(30));
    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedProcessInstanceForTraceId(eventProcessId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build(),
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(endDateTime)
                      .endDate(endDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );
        }
      );
  }

  @Test
  public void newEventsWithSameIdUpdateActivityInstances() {
    // given
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final String ingestedStartEventName = "startedEvent";
    final String ingestedEndEventName = "finishedEvent";

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    eventProcessClient.publishEventProcessMapping(eventProcessId);
    executeImportCycle();

    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime firstEndDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, startDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(10));
    final OffsetDateTime updatedEndDateTime = LocalDateUtil.getCurrentDateTime();
    ingestTestEvent(ingestedEndEventId, ingestedEndEventName, updatedEndDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(30));
    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedProcessInstanceForTraceId(eventProcessId, startDateTime, updatedEndDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build(),
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(updatedEndDateTime)
                      .endDate(updatedEndDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );
        }
      );
  }

  @Test
  public void instanceIsRunningIfNoEndEventYetIngested() {
    // given

    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final String ingestedStartEventName = "startedEvent";
    final String ingestedEndEventName = "finishedEvent";

    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              ProcessInstanceDto.builder()
                .processDefinitionId(eventProcessId)
                .processDefinitionKey(eventProcessId)
                .processDefinitionVersion("1")
                .processInstanceId(MY_TRACE_ID_1)
                .duration(null)
                .engine(null)
                .tenantId(null)
                .businessKey(null)
                .userTasks(null)
                .startDate(startDateTime)
                .endDate(null)
                .state("ACTIVE")
                .variables(Collections.singletonList(
                  SimpleProcessVariableDto.builder()
                    .id(VARIABLE_ID)
                    .name(VARIABLE_ID)
                    .value(VARIABLE_VALUE)
                    .version(1L)
                    .build()
                ))
                .build(),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build()
                  )
            );
        }
      );
  }

  @Test
  public void instanceIsCompletedOnceEndEventGotIngested() {
    // given
    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(60));
    final String ingestedStartEventName = "startedEvent";
    final String ingestedEndEventName = "finishedEvent";

    final OffsetDateTime startDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedStartEventId = ingestTestEvent(ingestedStartEventName, startDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, endDateTime);

    LocalDateUtil.setCurrentTime(timeBaseLine.plusSeconds(30));
    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedProcessInstanceForTraceId(eventProcessId, startDateTime, endDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedStartEventId)
                      .startDate(startDateTime)
                      .endDate(startDateTime)
                      .durationInMs(0L)
                      .activityType("startEvent")
                      .activityId(BPMN_START_EVENT_ID)
                      .build(),
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(endDateTime)
                      .endDate(endDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );
        }
      );
  }

  @Test
  public void instanceIsCompletedEvenIfOnlyEndEventGotIngested() {
    // given
    final OffsetDateTime timeBaseLine = OffsetDateTime.now();
    final String ingestedStartEventName = "startedEvent";
    final String ingestedEndEventName = "finishedEvent";

    LocalDateUtil.setCurrentTime(timeBaseLine.minusSeconds(30));
    final OffsetDateTime endDateTime = LocalDateUtil.getCurrentDateTime();
    final String ingestedEndEventId = ingestTestEvent(ingestedEndEventName, endDateTime);

    final String eventProcessId = createSimpleEventProcessMapping(ingestedStartEventName, ingestedEndEventName);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    eventProcessClient.publishEventProcessMapping(eventProcessId);

    executeImportCycle();

    // then
    final List<ProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        processInstanceDto -> {
          assertThat(processInstanceDto)
            .isEqualToIgnoringGivenFields(
              createExpectedCompletedProcessInstanceForTraceId(eventProcessId, null, endDateTime),
              ProcessInstanceDto.Fields.events
            )
            .extracting(ProcessInstanceDto::getEvents)
            .satisfies(
              simpleEventDtos ->
                assertThat(simpleEventDtos)
                  .containsOnly(
                    SimpleEventDto.builder()
                      .id(ingestedEndEventId)
                      .startDate(endDateTime)
                      .endDate(endDateTime)
                      .durationInMs(0L)
                      .activityType("endEvent")
                      .activityId(BPMN_END_EVENT_ID)
                      .build()
                  )
            );
        }
      );
  }

  private ProcessInstanceDto createExpectedCompletedProcessInstanceForTraceId(final String eventProcessId,
                                                                              final OffsetDateTime startDateTime,
                                                                              final OffsetDateTime endDateTime) {
    Long duration = null;
    if (startDateTime != null && endDateTime != null) {
      duration = startDateTime.until(endDateTime, ChronoUnit.MILLIS);
    }
    return ProcessInstanceDto.builder()
      .processDefinitionId(eventProcessId)
      .processDefinitionKey(eventProcessId)
      .processDefinitionVersion("1")
      .processInstanceId(MY_TRACE_ID_1)
      .duration(duration)
      .engine(null)
      .tenantId(null)
      .businessKey(null)
      .userTasks(null)
      .startDate(startDateTime)
      .endDate(endDateTime)
      .state("COMPLETED")
      .variables(Collections.singletonList(
        SimpleProcessVariableDto.builder()
          .id(VARIABLE_ID)
          .name(VARIABLE_ID)
          .value(VARIABLE_VALUE)
          .version(1L)
          .build()
      ))
      .build();
  }

  private String createSimpleEventProcessMapping(final String ingestedStartEventName,
                                                 final String ingestedEndEventName) {
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(
      BPMN_START_EVENT_ID,
      EventMappingDto.builder()
        .start(EventTypeDto.builder().group(EVENT_GROUP).source(EVENT_SOURCE).eventName(ingestedStartEventName).build())
        .build()
    );
    eventMappings.put(
      BPMN_END_EVENT_ID,
      EventMappingDto.builder()
        .start(EventTypeDto.builder().group(EVENT_GROUP).source(EVENT_SOURCE).eventName(ingestedEndEventName).build())
        .build()
    );
    EventProcessMappingDto eventProcessMappingDto = eventProcessClient.createEventProcessMappingDtoWithMappingsWithXml(
      eventMappings, "myEventProcess", createSimpleProcessDefinitionXml()
    );
    return eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
  }

  @SneakyThrows
  private void executeImportCycle() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getIngestedEventImportScheduler()
      .runImportCycle()
      .get(10, TimeUnit.SECONDS);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @SneakyThrows
  private List<String> getEventProcessInstanceIndicesFromElasticsearch() {
    final OptimizeIndexNameService indexNameService = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .getIndexNameService();
    final GetIndexResponse getIndexResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .getHighLevelClient()
      .indices().get(
        new GetIndexRequest().indices(
          indexNameService.getOptimizeIndexAliasForIndex(EventProcessInstanceIndex.EVENT_PROCESS_INSTANCE_INDEX_PREFIX)
            + "*"
        ),
        RequestOptions.DEFAULT
      );
    return Lists.newArrayList(getIndexResponse.indices());
  }

  @SneakyThrows
  private Optional<EventProcessPublishStateDto> getEventProcessPublishStateDtoFromElasticsearch(final String eventProcessMappingId) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(
        boolQuery()
          .must(termQuery(EventProcessPublishStateIndex.PROCESS_MAPPING_ID, eventProcessMappingId))
          .must(termQuery(EventProcessPublishStateIndex.DELETED, false))
      )
      .sort(SortBuilders.fieldSort(EventProcessPublishStateIndex.PUBLISH_DATE_TIME).order(SortOrder.DESC))
      .size(1);
    final SearchResponse searchResponse = elasticSearchIntegrationTestExtension
      .getOptimizeElasticClient()
      .search(new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX).source(searchSourceBuilder), RequestOptions.DEFAULT);

    EventProcessPublishStateDto result = null;
    if (searchResponse.getHits().totalHits > 0) {
      result = elasticSearchIntegrationTestExtension.getObjectMapper().readValue(
        searchResponse.getHits().getAt(0).getSourceAsString(),
        IndexableEventProcessPublishStateDto.class
      ).toEventProcessPublishStateDto();
    }

    return Optional.ofNullable(result);
  }

  @SneakyThrows
  private List<ProcessInstanceDto> getEventProcessInstancesFromElasticsearch() {
    final List<ProcessInstanceDto> results = new ArrayList<>();
    final SearchResponse searchResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .search(
        new SearchRequest(EventProcessInstanceIndex.EVENT_PROCESS_INSTANCE_INDEX_PREFIX + "*"),
        RequestOptions.DEFAULT
      );
    for (SearchHit hit : searchResponse.getHits().getHits()) {
      results.add(
        elasticSearchIntegrationTestExtension.getObjectMapper()
          .readValue(hit.getSourceAsString(), ProcessInstanceDto.class)
      );
    }
    return results;
  }

  private String ingestTestEvent(final String event, final OffsetDateTime eventTimestamp) {
    return ingestTestEvent(
      IdGenerator.getNextId(), event, eventTimestamp
    );
  }

  private String ingestTestEvent(final String eventId, final String event, final OffsetDateTime eventTimestamp) {
    embeddedOptimizeExtension.getEventService()
      .saveEvent(new EventDto(
        eventId,
        event,
        eventTimestamp.toInstant().toEpochMilli(),
        null,
        MY_TRACE_ID_1,
        null,
        EVENT_GROUP,
        EVENT_SOURCE,
        ImmutableMap.of(VARIABLE_ID, VARIABLE_VALUE)
      ));
    return eventId;
  }

  @SneakyThrows
  protected String createSimpleProcessDefinitionXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(BPMN_START_EVENT_ID)
      .endEvent(BPMN_END_EVENT_ID)
      .done();
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, bpmnModel);
    return new String(xmlOutput.toByteArray(), StandardCharsets.UTF_8);
  }

}
