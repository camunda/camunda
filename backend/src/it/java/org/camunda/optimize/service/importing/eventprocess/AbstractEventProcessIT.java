/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.rest.event.EventSourceEntryRestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class AbstractEventProcessIT extends AbstractIT {
  protected static final String MY_TRACE_ID_1 = "myTraceId1";

  protected static final String BPMN_START_EVENT_ID = "StartEvent_1";
  protected static final String BPMN_INTERMEDIATE_EVENT_ID = "IntermediateEvent_1";
  protected static final String BPMN_INTERMEDIATE_EVENT_ID_TWO = "IntermediateEvent_2";
  protected static final String BPMN_END_EVENT_ID = "EndEvent_2";
  protected static final String USER_TASK_ID_ONE = "user_task_1";
  protected static final String USER_TASK_ID_TWO = "user_task_2";
  protected static final String USER_TASK_ID_THREE = "user_task_3";
  protected static final String USER_TASK_ID_FOUR = "user_task_4";
  protected static final String SPLITTING_GATEWAY_ID = "splitting_gateway";
  protected static final String SPLITTING_GATEWAY_ID_TWO = "splitting_gateway_two";
  protected static final String SPLITTING_GATEWAY_ID_THREE = "splitting_gateway_three";
  protected static final String SPLITTING_GATEWAY_ID_FOUR = "splitting_gateway_four";
  protected static final String MERGING_GATEWAY_ID = "merging_gateway";
  protected static final String MERGING_GATEWAY_ID_TWO = "merging_gateway_two";
  protected static final String MERGING_GATEWAY_ID_THREE = "merging_gateway_three";
  protected static final String MERGING_GATEWAY_ID_FOUR = "merging_gateway_four";
  protected static final String VARIABLE_ID = "var";
  protected static final String VARIABLE_VALUE = "value";
  protected static final String EVENT_GROUP = "test";
  protected static final String EVENT_SOURCE = "integrationTest";
  protected static final String EVENT_PROCESS_NAME = "myEventProcess";

  protected static final String STARTED_EVENT = "startedEvent";
  protected static final String FINISHED_EVENT = "finishedEvent";
  protected static final String START_EVENT_TYPE = "startEvent";
  protected static final String END_EVENT_TYPE = "endEvent";
  protected static final String EXCLUSIVE_GATEWAY_TYPE = "exclusiveGateway";
  protected static final String PARALLEL_GATEWAY_TYPE = "parallelGateway";
  protected static final String EVENT_BASED_GATEWAY_TYPE = "eventBasedGateway";
  protected static final String USER_TASK_TYPE = "userTask";
  protected static final String INTERMEDIATE_CATCH_EVENT_TYPE = "intermediateCatchEvent";


  protected static final String PROCESS_INSTANCE_STATE_COMPLETED = "COMPLETED";
  protected static final String PROCESS_INSTANCE_STATE_ACTIVE = "ACTIVE";

  protected static final String FIRST_EVENT_NAME = "firstEvent";
  protected static final String SECOND_EVENT_NAME = "secondEvent";
  protected static final String THIRD_EVENT_NAME = "thirdEvent";
  protected static final String FOURTH_EVENT_NAME = "fourthEvent";
  protected static final String FIFTH_EVENT_NAME = "fifthEvent";

  protected static final OffsetDateTime FIRST_EVENT_DATETIME = OffsetDateTime.parse("2019-12-12T12:00:00.000+01:00");
  protected static final OffsetDateTime SECOND_EVENT_DATETIME = OffsetDateTime.parse("2019-12-12T12:00:30.000+01:00");
  protected static final OffsetDateTime THIRD_EVENT_DATETIME = OffsetDateTime.parse("2019-12-12T12:00:45.000+01:00");
  protected static final OffsetDateTime FOURTH_EVENT_DATETIME = OffsetDateTime.parse("2019-12-12T12:01:00.000+01:00");
  protected static final OffsetDateTime FIFTH_EVENT_DATETIME = OffsetDateTime.parse("2019-12-12T12:02:00.000+01:00");

  @BeforeEach
  public void enableEventBasedProcessFeature() {
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(true);
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().getAuthorizedUserIds()
      .add(DEFAULT_USERNAME);
  }

  protected static Stream<Arguments> cancelOrDeleteAction() {
    return Stream.of(
      Arguments.arguments(
        "cancelPublish",
        (BiConsumer<EventProcessClient, String>) EventProcessClient::cancelPublishEventProcessMapping
      ),
      Arguments.arguments(
        "deleteEventProcessMapping",
        (BiConsumer<EventProcessClient, String>) EventProcessClient::deleteEventProcessMapping
      )
    );
  }

  protected String createEventProcessMappingFromEventMappings(final EventMappingDto startEventMapping,
                                                              final EventMappingDto intermediateEventMapping,
                                                              final EventMappingDto endEventMapping) {
    final EventProcessMappingDto eventProcessMappingDto = buildSimpleEventProcessMappingDto(
      startEventMapping, intermediateEventMapping, endEventMapping
    );
    eventProcessMappingDto.setXml(createThreeActivitiesProcessDefinitionXml());
    return eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
  }

  protected String createSimpleEventProcessMapping(final String ingestedStartEventName,
                                                   final String ingestedEndEventName) {
    final EventProcessMappingDto eventProcessMappingDto = buildSimpleEventProcessMappingDto(
      ingestedStartEventName, ingestedEndEventName
    );
    eventProcessMappingDto.setXml(createSimpleProcessDefinitionXml());
    return eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
  }

  protected EventProcessMappingDto buildSimpleEventProcessMappingDto(final String ingestedStartEventName,
                                                                     final String ingestedEndEventName) {
    return buildSimpleEventProcessMappingDto(
      EventMappingDto.builder()
        .end(EventTypeDto.builder().group(EVENT_GROUP).source(EVENT_SOURCE).eventName(ingestedStartEventName).build())
        .build(),
      null,
      EventMappingDto.builder()
        .end(EventTypeDto.builder().group(EVENT_GROUP).source(EVENT_SOURCE).eventName(ingestedEndEventName).build())
        .build()
    );
  }

  private EventProcessMappingDto buildSimpleEventProcessMappingDto(final EventMappingDto startEventMapping,
                                                                     final EventMappingDto intermediateEventMapping,
                                                                     final EventMappingDto endEventMapping) {
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    Optional.ofNullable(startEventMapping)
      .ifPresent(mapping -> eventMappings.put(BPMN_START_EVENT_ID, mapping));
    Optional.ofNullable(intermediateEventMapping)
      .ifPresent(mapping -> eventMappings.put(BPMN_INTERMEDIATE_EVENT_ID, mapping));
    Optional.ofNullable(endEventMapping)
      .ifPresent(mapping -> eventMappings.put(BPMN_END_EVENT_ID, mapping));
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      eventMappings, EVENT_PROCESS_NAME, createSimpleProcessDefinitionXml()
    );
  }

  protected EventSourceEntryDto convertToEventSourceEntryDto(EventSourceEntryRestDto eventSourceRestEntry) {
    return EventSourceEntryDto.builder()
      .id(eventSourceRestEntry.getId())
      .type(eventSourceRestEntry.getType())
      .eventScope(eventSourceRestEntry.getEventScope())
      .processDefinitionKey(eventSourceRestEntry.getProcessDefinitionKey())
      .versions(eventSourceRestEntry.getVersions())
      .tenants(eventSourceRestEntry.getTenants())
      .tracedByBusinessKey(eventSourceRestEntry.getTracedByBusinessKey())
      .traceVariable(eventSourceRestEntry.getTraceVariable())
      .build();
  }

  @SneakyThrows
  protected void executeImportCycle() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler()
      .runImportCycle()
      .get(10, TimeUnit.SECONDS);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @SneakyThrows
  protected Map<String, List<AliasMetaData>> getEventProcessInstanceIndicesWithAliasesFromElasticsearch() {
    final OptimizeIndexNameService indexNameService = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .getIndexNameService();
    final GetIndexResponse getIndexResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .getHighLevelClient()
      .indices().get(
        new GetIndexRequest().indices(
          indexNameService
            .getOptimizeIndexAliasForIndex(EVENT_PROCESS_INSTANCE_INDEX_PREFIX)
            + "*"
        ),
        RequestOptions.DEFAULT
      );
    return StreamSupport.stream(getIndexResponse.aliases().spliterator(), false)
      .collect(Collectors.toMap(cursor -> cursor.key, cursor -> cursor.value));
  }

  @SneakyThrows
  protected Optional<EventProcessPublishStateDto> getEventProcessPublishStateDtoFromElasticsearch(final String processMappingId) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(
        boolQuery()
          .must(termQuery(EventProcessPublishStateIndex.PROCESS_MAPPING_ID, processMappingId))
          .must(termQuery(EventProcessPublishStateIndex.DELETED, false))
      )
      .sort(SortBuilders.fieldSort(EventProcessPublishStateIndex.PUBLISH_DATE_TIME).order(SortOrder.DESC))
      .size(1);
    final SearchResponse searchResponse = elasticSearchIntegrationTestExtension
      .getOptimizeElasticClient()
      .search(new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX).source(searchSourceBuilder), RequestOptions.DEFAULT);

    EventProcessPublishStateDto result = null;
    if (searchResponse.getHits().getTotalHits().value > 0) {
      result = elasticSearchIntegrationTestExtension.getObjectMapper().readValue(
        searchResponse.getHits().getAt(0).getSourceAsString(),
        IndexableEventProcessPublishStateDto.class
      ).toEventProcessPublishStateDto();
    }

    return Optional.ofNullable(result);
  }

  @SneakyThrows
  protected Optional<EventProcessDefinitionDto> getEventProcessDefinitionFromElasticsearch(final String definitionId) {
    final GetResponse getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .get(new GetRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME).id(definitionId), RequestOptions.DEFAULT);

    EventProcessDefinitionDto result = null;
    if (getResponse.isExists()) {
      result = elasticSearchIntegrationTestExtension.getObjectMapper().readValue(
        getResponse.getSourceAsString(), EventProcessDefinitionDto.class
      );
    }

    return Optional.ofNullable(result);
  }

  @SneakyThrows
  protected List<ProcessInstanceDto> getEventProcessInstancesFromElasticsearch() {
    return getEventProcessInstancesFromElasticsearchForProcessMappingId("*");
  }

  @SneakyThrows
  protected List<ProcessInstanceDto> getEventProcessInstancesFromElasticsearchForProcessMappingId(final String eventProcessMappingId) {
    final List<ProcessInstanceDto> results = new ArrayList<>();
    final SearchResponse searchResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .search(
        new SearchRequest(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + eventProcessMappingId),
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

  protected String ingestTestEvent(final String event) {
    return ingestTestEvent(IdGenerator.getNextId(), event, OffsetDateTime.now());
  }

  protected String ingestTestEvent(final String event, final OffsetDateTime eventTimestamp) {
    return ingestTestEvent(IdGenerator.getNextId(), event, eventTimestamp);
  }

  protected String ingestTestEvent(final String eventId, final String eventName, final OffsetDateTime eventTimestamp) {
    embeddedOptimizeExtension.getEventService()
      .saveEventBatch(
        Collections.singletonList(
          EventDto.builder()
            .id(eventId)
            .eventName(eventName)
            .timestamp(eventTimestamp.toInstant().toEpochMilli())
            .traceId(MY_TRACE_ID_1)
            .group(EVENT_GROUP)
            .source(EVENT_SOURCE)
            .data(ImmutableMap.of(VARIABLE_ID, VARIABLE_VALUE))
            .build()
        )
      );
    return eventId;
  }

  protected String getEventPublishStateIdForEventProcessMappingId(final String eventProcessMappingId) {
    return getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId)
      .map(EventProcessPublishStateDto::getId)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Could not get id of published process"));
  }

  protected static EventMappingDto startMapping(final String eventName) {
    return EventMappingDto.builder()
      .start(EventTypeDto.builder().group(EVENT_GROUP).source(EVENT_SOURCE).eventName(eventName).build())
      .build();
  }

  protected static EventMappingDto endMapping(final String eventName) {
    return EventMappingDto.builder()
      .end(EventTypeDto.builder().group(EVENT_GROUP).source(EVENT_SOURCE).eventName(eventName).build())
      .build();
  }

  protected static EventMappingDto startAndEndMapping(final String startEventName, final String endEventName) {
    return EventMappingDto.builder()
      .start(EventTypeDto.builder().group(EVENT_GROUP).source(EVENT_SOURCE).eventName(startEventName).build())
      .end(EventTypeDto.builder().group(EVENT_GROUP).source(EVENT_SOURCE).eventName(endEventName).build())
      .build();
  }

  @SneakyThrows
  protected String createThreeActivitiesProcessDefinitionXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(BPMN_START_EVENT_ID)
      .intermediateCatchEvent(BPMN_INTERMEDIATE_EVENT_ID)
      .endEvent(BPMN_END_EVENT_ID)
      .done();
    return convertBpmnModelToXmlString(bpmnModel);
  }

  @SneakyThrows
  protected static String createSimpleProcessDefinitionXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(BPMN_START_EVENT_ID)
      .endEvent(BPMN_END_EVENT_ID)
      .done();
    return convertBpmnModelToXmlString(bpmnModel);
  }

  @SneakyThrows
  protected static String createExclusiveGatewayProcessDefinitionXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name(EVENT_PROCESS_NAME)
      .startEvent(BPMN_START_EVENT_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_ONE)
      .exclusiveGateway(MERGING_GATEWAY_ID)
      .endEvent(BPMN_END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_TWO)
      .connectTo(MERGING_GATEWAY_ID)
      .done();
    return convertBpmnModelToXmlString(bpmnModel);
  }

  @SneakyThrows
  protected static String createParallelGatewayProcessDefinitionXml() {
    // @formatter:off
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name(EVENT_PROCESS_NAME)
      .startEvent(BPMN_START_EVENT_ID)
      .parallelGateway(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_ONE)
      .parallelGateway(MERGING_GATEWAY_ID)
      .endEvent(BPMN_END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_TWO)
      .connectTo(MERGING_GATEWAY_ID)
      .done();
    // @formatter:on
    return convertBpmnModelToXmlString(bpmnModel);
  }

  @SneakyThrows
  protected static String createEventBasedGatewayProcessDefinitionXml() {
    // @formatter:off
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name(EVENT_PROCESS_NAME)
      .startEvent(BPMN_START_EVENT_ID)
      .eventBasedGateway().id(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_ONE)
      .exclusiveGateway(MERGING_GATEWAY_ID)
      .endEvent(BPMN_END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
      .intermediateCatchEvent(BPMN_INTERMEDIATE_EVENT_ID_TWO)
      .connectTo(MERGING_GATEWAY_ID)
      .done();
    // @formatter:on
    return convertBpmnModelToXmlString(bpmnModel);
  }

  @SneakyThrows
  protected static String createExclusiveGatewayProcessDefinitionWithConsecutiveGatewaysXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name(EVENT_PROCESS_NAME)
      .startEvent(BPMN_START_EVENT_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_ONE)
      .exclusiveGateway(MERGING_GATEWAY_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID_TWO)
      .userTask(USER_TASK_ID_TWO)
      .exclusiveGateway(MERGING_GATEWAY_ID_TWO)
      .endEvent(BPMN_END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
      .connectTo(MERGING_GATEWAY_ID)
      .moveToNode(SPLITTING_GATEWAY_ID_TWO)
      .connectTo(MERGING_GATEWAY_ID_TWO)
      .done();
    return convertBpmnModelToXmlString(bpmnModel);
  }

  @SneakyThrows
  protected static String createExclusiveGatewayProcessDefinitionWithLoopXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess()
      .camundaVersionTag("aVersionTag")
      .name(EVENT_PROCESS_NAME)
      .startEvent(BPMN_START_EVENT_ID)
      .exclusiveGateway(MERGING_GATEWAY_ID)
      .userTask(USER_TASK_ID_ONE)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
      .endEvent(BPMN_END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_TWO)
      .connectTo(MERGING_GATEWAY_ID)
      .done();
    return convertBpmnModelToXmlString(bpmnModel);
  }

  @SneakyThrows
  protected static String createExclusiveGatewayProcessDefinitionWithThreeConsecutiveGatewaysAndLoopXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess()
      .camundaVersionTag("aVersionTag")
      .name(EVENT_PROCESS_NAME)
      .startEvent(BPMN_START_EVENT_ID)
      .exclusiveGateway(MERGING_GATEWAY_ID_FOUR)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID_TWO)
      .exclusiveGateway(SPLITTING_GATEWAY_ID_THREE)
      .userTask(USER_TASK_ID_ONE)
      .exclusiveGateway(MERGING_GATEWAY_ID_THREE)
      .exclusiveGateway(MERGING_GATEWAY_ID_TWO)
      .exclusiveGateway(MERGING_GATEWAY_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID_FOUR)
      .endEvent(BPMN_END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID_THREE)
      .userTask(USER_TASK_ID_TWO)
      .connectTo(MERGING_GATEWAY_ID_THREE)
      .moveToNode(SPLITTING_GATEWAY_ID_TWO)
      .userTask(USER_TASK_ID_THREE)
      .connectTo(MERGING_GATEWAY_ID_TWO)
      .moveToNode(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_FOUR)
      .connectTo(MERGING_GATEWAY_ID)
      .moveToNode(SPLITTING_GATEWAY_ID_FOUR)
      .connectTo(MERGING_GATEWAY_ID_FOUR)
      .done();
    return convertBpmnModelToXmlString(bpmnModel);
  }

  @SneakyThrows
  protected static String createExclusiveGatewayProcessDefinitionWithEventBeforeGatewayXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name(EVENT_PROCESS_NAME)
      .startEvent(BPMN_START_EVENT_ID)
      .userTask(USER_TASK_ID_ONE)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_TWO)
      .exclusiveGateway(MERGING_GATEWAY_ID)
      .endEvent(BPMN_END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
      .connectTo(MERGING_GATEWAY_ID)
      .done();
    return convertBpmnModelToXmlString(bpmnModel);
  }

  @SneakyThrows
  protected static String createInclusiveGatewayProcessDefinitionXml() {
    // @formatter:off
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name(EVENT_PROCESS_NAME)
      .startEvent(BPMN_START_EVENT_ID)
      .inclusiveGateway(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_ONE)
      .inclusiveGateway(MERGING_GATEWAY_ID)
      .endEvent(BPMN_END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_TWO)
      .connectTo(MERGING_GATEWAY_ID)
      .done();
    // @formatter:on
    return convertBpmnModelToXmlString(bpmnModel);
  }

  @SneakyThrows
  protected static String createExclusiveGatewayProcessDefinitionWithMixedDirectionGatewaysXml() {
    // @formatter:off
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name(EVENT_PROCESS_NAME)
      .startEvent(BPMN_START_EVENT_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_ONE)
      .exclusiveGateway(MERGING_GATEWAY_ID)
      .endEvent(BPMN_END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
      .userTask(USER_TASK_ID_THREE)
      .connectTo(MERGING_GATEWAY_ID)
      .moveToNode(MERGING_GATEWAY_ID)
      .userTask(USER_TASK_ID_TWO)
      .connectTo(SPLITTING_GATEWAY_ID)
      .done();
    // @formatter:on
    return convertBpmnModelToXmlString(bpmnModel);
  }

  protected static String convertBpmnModelToXmlString(final BpmnModelInstance bpmnModel) {
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, bpmnModel);
    return new String(xmlOutput.toByteArray(), StandardCharsets.UTF_8);
  }
}
