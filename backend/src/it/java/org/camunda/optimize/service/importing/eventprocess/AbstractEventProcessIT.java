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
import org.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.importing.BackoffImportMediator;
import org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.InstanceIndexUtil;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.cluster.metadata.AliasMetadata;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.events.CamundaEventService.EVENT_SOURCE_CAMUNDA;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventAllGroupsSourceEntry;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventSourceEntryForGroup;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class AbstractEventProcessIT extends AbstractIT {
  protected static final String MY_TRACE_ID_1 = "myTraceId1";

  protected static final String BPMN_START_EVENT_ID = "StartEvent_1";
  protected static final String BPMN_INTERMEDIATE_EVENT_ID = "IntermediateEvent_1";
  protected static final String BPMN_INTERMEDIATE_EVENT_ID_TWO = "IntermediateEvent_2";
  protected static final String BPMN_END_EVENT_ID = "EndEvent_1";
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
  public static final String EXTERNAL_EVENT_GROUP = "testGroup";
  public static final String EXTERNAL_EVENT_SOURCE = "integrationTestSource";
  protected static final String EVENT_PROCESS_NAME = "myEventProcess";

  protected static final String STARTED_EVENT = "startedEvent";
  protected static final String FINISHED_EVENT = "finishedEvent";
  protected static final String START_EVENT_TYPE = "startEvent";
  protected static final String END_EVENT_TYPE = "endEvent";
  protected static final String EXCLUSIVE_GATEWAY_TYPE = "exclusiveGateway";
  protected static final String PARALLEL_GATEWAY_TYPE = "parallelGateway";
  protected static final String EVENT_BASED_GATEWAY_TYPE = "eventBasedGateway";
  protected static final String USER_TASK_TYPE = "userTask";

  protected static final String PROCESS_INSTANCE_STATE_COMPLETED = ProcessInstanceConstants.COMPLETED_STATE;
  protected static final String PROCESS_INSTANCE_STATE_ACTIVE = ProcessInstanceConstants.ACTIVE_STATE;

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

  protected static final String[] NULLABLE_FLOW_NODE_FIELDS_TO_IGNORE = new String[]{
    FlowNodeInstanceDto.Fields.canceled,
    FlowNodeInstanceDto.Fields.tenantId,
    FlowNodeInstanceDto.Fields.engine,
    FlowNodeInstanceDto.Fields.userTaskInstanceId,
    FlowNodeInstanceDto.Fields.dueDate,
    FlowNodeInstanceDto.Fields.deleteReason,
    FlowNodeInstanceDto.Fields.assignee,
    FlowNodeInstanceDto.Fields.candidateGroups,
    FlowNodeInstanceDto.Fields.assigneeOperations,
    FlowNodeInstanceDto.Fields.candidateGroupOperations,
    FlowNodeInstanceDto.Fields.idleDurationInMs,
    FlowNodeInstanceDto.Fields.workDurationInMs};

  @BeforeEach
  public void enableEventBasedProcessFeature() {
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .getEventImport()
      .setEnabled(true);
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().getAuthorizedUserIds()
      .add(DEFAULT_USERNAME);
  }

  // it's used as test param via @MethodSource
  @SuppressWarnings(UNUSED)
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

  protected void republishEventProcess(final String eventProcessMapping) {
    eventProcessClient.cancelPublishEventProcessMapping(eventProcessMapping);
    publishEventProcess(eventProcessMapping);
  }

  protected void publishEventProcess(final String eventProcessMapping) {
    eventProcessClient.publishEventProcessMapping(eventProcessMapping);
    executeImportCycle();
    // second cycle to make sure event process publish states are updated based on the previous cycle
    executeImportCycle();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected void createAndPublishEventProcessMapping(final Map<String, EventMappingDto> eventMappings, String bpmnXml) {
    final EventProcessMappingDto eventProcessMappingDto = eventProcessClient
      .buildEventProcessMappingDtoWithMappingsAndExternalEventSource(eventMappings, EVENT_PROCESS_NAME, bpmnXml);
    final String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessClient.publishEventProcessMapping(eventProcessId);
  }

  protected String createEventProcessMappingFromEventMappings(final EventMappingDto startEventMapping,
                                                              final EventMappingDto intermediateEventMapping,
                                                              final EventMappingDto endEventMapping) {
    final EventProcessMappingDto eventProcessMappingDto = buildSimpleEventProcessMappingDto(
      startEventMapping, intermediateEventMapping, endEventMapping
    );
    eventProcessMappingDto.setXml(createTwoEventAndOneTaskActivitiesProcessDefinitionXml());
    return eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
  }

  protected String createSimpleEventProcessMapping(final String ingestedStartEventName,
                                                   final String ingestedEndEventName) {
    final EventProcessMappingDto eventProcessMappingDto = buildSimpleEventProcessMappingDto(
      ingestedStartEventName, ingestedEndEventName
    );
    eventProcessMappingDto.setXml(createTwoEventAndOneTaskActivitiesProcessDefinitionXml());
    return eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
  }

  protected EventProcessMappingDto buildSimpleEventProcessMappingDto(final String ingestedStartEventName,
                                                                     final String ingestedEndEventName) {
    return buildSimpleEventProcessMappingDto(
      EventMappingDto.builder()
        .end(EventTypeDto.builder()
               .group(EXTERNAL_EVENT_GROUP)
               .source(EXTERNAL_EVENT_SOURCE)
               .eventName(ingestedStartEventName)
               .build())
        .build(),
      null,
      EventMappingDto.builder()
        .end(EventTypeDto.builder()
               .group(EXTERNAL_EVENT_GROUP)
               .source(EXTERNAL_EVENT_SOURCE)
               .eventName(ingestedEndEventName)
               .build())
        .build()
    );
  }

  protected EventProcessMappingDto buildSimpleEventProcessMappingDto(final EventMappingDto startEventMapping,
                                                                     final EventMappingDto intermediateEventMapping,
                                                                     final EventMappingDto endEventMapping) {
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    Optional.ofNullable(startEventMapping)
      .ifPresent(mapping -> eventMappings.put(BPMN_START_EVENT_ID, mapping));
    Optional.ofNullable(intermediateEventMapping)
      .ifPresent(mapping -> eventMappings.put(USER_TASK_ID_ONE, mapping));
    Optional.ofNullable(endEventMapping)
      .ifPresent(mapping -> eventMappings.put(BPMN_END_EVENT_ID, mapping));
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      eventMappings, EVENT_PROCESS_NAME, createTwoEventAndOneTaskActivitiesProcessDefinitionXml()
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

  protected void importEngineEntities() {
    importAllEngineEntitiesFromLastIndex();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @SneakyThrows
  protected Map<String, List<AliasMetadata>> getEventProcessInstanceIndicesWithAliasesFromElasticsearch() {
    final OptimizeElasticsearchClient esClient =
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    final OptimizeIndexNameService indexNameService = esClient
      .getIndexNameService();
    final GetIndexResponse getIndexResponse = esClient
      .getHighLevelClient()
      .indices().get(
        new GetIndexRequest(indexNameService.getOptimizeIndexAliasForIndex(EVENT_PROCESS_INSTANCE_INDEX_PREFIX) + "*"),
        esClient.requestOptions()
      );
    return getIndexResponse.getAliases();
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
      .search(
        new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME).source(searchSourceBuilder)
      );

    EventProcessPublishStateDto result = null;
    if (searchResponse.getHits().getTotalHits().value > 0) {
      result = elasticSearchIntegrationTestExtension.getObjectMapper().readValue(
        searchResponse.getHits().getAt(0).getSourceAsString(),
        EsEventProcessPublishStateDto.class
      ).toEventProcessPublishStateDto();
    }
    return Optional.ofNullable(result);
  }

  @SneakyThrows
  protected Optional<EventProcessDefinitionDto> getEventProcessDefinitionFromElasticsearch(final String definitionId) {
    final GetResponse getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .get(new GetRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME).id(definitionId));

    EventProcessDefinitionDto result = null;
    if (getResponse.isExists()) {
      result = elasticSearchIntegrationTestExtension.getObjectMapper().readValue(
        getResponse.getSourceAsString(), EventProcessDefinitionDto.class
      );
    }

    return Optional.ofNullable(result);
  }

  @SneakyThrows
  protected List<EventProcessInstanceDto> getEventProcessInstancesFromElasticsearch() {
    return getEventProcessInstancesFromElasticsearchForProcessPublishStateId("*");
  }

  @SneakyThrows
  protected List<EventProcessInstanceDto> getEventProcessInstancesFromElasticsearchForProcessPublishStateId(final String publishStateId) {
    final List<EventProcessInstanceDto> results = new ArrayList<>();
    final SearchResponse searchResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .search(new SearchRequest(new EventProcessInstanceIndex(publishStateId).getIndexName()));
    for (SearchHit hit : searchResponse.getHits().getHits()) {
      results.add(
        elasticSearchIntegrationTestExtension.getObjectMapper()
          .readValue(hit.getSourceAsString(), EventProcessInstanceDto.class)
      );
    }
    return results;
  }

  protected String ingestTestEvent(final String eventName) {
    return ingestTestEvent(IdGenerator.getNextId(), eventName, OffsetDateTime.now(), MY_TRACE_ID_1);
  }

  protected String ingestTestEvent(final String eventName, final String traceId) {
    return ingestTestEvent(IdGenerator.getNextId(), eventName, OffsetDateTime.now(), traceId);
  }

  protected String ingestTestEvent(final String eventName, final OffsetDateTime eventTimestamp) {
    return ingestTestEvent(IdGenerator.getNextId(), eventName, eventTimestamp, MY_TRACE_ID_1);
  }

  protected String ingestTestEvent(final String eventName, final OffsetDateTime eventTimestamp, final String traceId) {
    return ingestTestEvent(IdGenerator.getNextId(), eventName, eventTimestamp, traceId);
  }

  protected String ingestTestEvent(final String eventId, final String eventName, final OffsetDateTime eventTimestamp) {
    return ingestTestEvent(eventId, eventName, eventTimestamp, MY_TRACE_ID_1);
  }

  protected String ingestTestEvent(final String eventId,
                                   final String eventName,
                                   final OffsetDateTime eventTimestamp,
                                   final String traceId) {
    return ingestTestEvent(eventId, eventName, eventTimestamp, traceId, EXTERNAL_EVENT_GROUP);
  }

  protected String ingestTestEvent(final String eventId,
                                   final String eventName,
                                   final OffsetDateTime eventTimestamp,
                                   final String traceId,
                                   final String group) {
    embeddedOptimizeExtension.getEventService()
      .saveEventBatch(
        Collections.singletonList(
          EventDto.builder()
            .id(eventId)
            .eventName(eventName)
            .timestamp(eventTimestamp.toInstant().toEpochMilli())
            .traceId(traceId)
            .group(group)
            .source(EXTERNAL_EVENT_SOURCE)
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

  protected EventProcessPublishStateDto getEventPublishStateForEventProcessMappingId(final String eventProcessMappingId) {
    return getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Could not get id of published process"));
  }

  protected static EventMappingDto startMapping(final String eventName) {
    return EventMappingDto.builder()
      .start(
        EventTypeDto.builder()
          .group(EXTERNAL_EVENT_GROUP)
          .source(EXTERNAL_EVENT_SOURCE)
          .eventName(eventName)
          .build()
      )
      .build();
  }

  protected static EventMappingDto endMapping(final String eventName) {
    return EventMappingDto.builder()
      .end(
        EventTypeDto.builder()
          .group(EXTERNAL_EVENT_GROUP)
          .source(EXTERNAL_EVENT_SOURCE)
          .eventName(eventName)
          .build())
      .build();
  }

  protected static EventMappingDto startAndEndMapping(final String startEventName, final String endEventName) {
    return EventMappingDto.builder()
      .start(
        EventTypeDto.builder()
          .group(EXTERNAL_EVENT_GROUP)
          .source(EXTERNAL_EVENT_SOURCE)
          .eventName(startEventName)
          .build())
      .end(
        EventTypeDto.builder()
          .group(EXTERNAL_EVENT_GROUP)
          .source(EXTERNAL_EVENT_SOURCE)
          .eventName(endEventName)
          .build())
      .build();
  }

  protected void assertProcessInstance(final ProcessInstanceDto processInstanceDto,
                                       final String expectedInstanceId,
                                       final List<String> expectedEventIds) {
    assertThat(processInstanceDto.getProcessInstanceId()).isEqualTo(expectedInstanceId);
    assertThat(processInstanceDto.getFlowNodeInstances())
      .satisfies(events -> assertThat(events)
        .extracting(
          FlowNodeInstanceDto::getFlowNodeId
        )
        .containsExactlyInAnyOrderElementsOf(expectedEventIds)
      );
  }

  protected ProcessInstanceEngineDto deployAndStartProcess() {
    return deployAndStartProcessWithVariables(Collections.emptyMap());
  }

  protected ProcessInstanceEngineDto deployAndStartProcessWithVariables(final Map<String, Object> variables) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent(BPMN_START_EVENT_ID)
      .userTask(USER_TASK_ID_ONE)
      .endEvent(BPMN_END_EVENT_ID)
      .done();
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.deployAndStartProcessWithVariables(modelInstance, variables);
    grantAuthorizationsToDefaultUser(instance.getProcessDefinitionKey());
    return instance;
  }

  protected ProcessInstanceEngineDto deployAndStartInstanceWithBusinessKey(final String businessKey) {
    return deployAndStartInstanceWithBusinessKey(businessKey, Collections.emptyMap());
  }

  protected ProcessInstanceEngineDto deployAndStartInstanceWithBusinessKey(final String businessKey,
                                                                           final Map<String, Object> variables) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      Bpmn.createExecutableProcess("aProcess")
        .startEvent(BPMN_START_EVENT_ID)
        .userTask(USER_TASK_ID_ONE)
        .endEvent(BPMN_END_EVENT_ID)
        .done(),
      variables,
      businessKey,
      null
    );
  }

  protected String publishEventMappingUsingProcessInstanceCamundaEvents(final ProcessInstanceEngineDto processInstanceEngineDto,
                                                                        final Map<String, EventMappingDto> eventMappings) {
    return publishEventMappingUsingProcessInstanceCamundaEventsAndTraceVariable(
      processInstanceEngineDto, eventMappings, null
    );
  }

  protected String publishEventMappingUsingProcessInstanceCamundaEventsAndTraceVariable(
    final ProcessInstanceEngineDto processInstanceEngineDto,
    final Map<String, EventMappingDto> eventMappings,
    final String traceVariable) {
    final CamundaEventSourceEntryDto eventSourceEntry =
      createCamundaEventSourceEntryForInstance(
        processInstanceEngineDto,
        traceVariable,
        Collections.singletonList(processInstanceEngineDto.getProcessDefinitionVersion())
      );
    return createAndPublishEventMapping(eventMappings, Collections.singletonList(eventSourceEntry));
  }

  protected String createAndPublishEventMapping(final Map<String, EventMappingDto> eventMappings,
                                                final List<EventSourceEntryDto<?>> eventSourceEntryDtos) {
    final EventProcessMappingDto eventProcessMappingDto =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
        eventMappings,
        UUID.randomUUID().toString(),
        createTwoEventAndOneTaskActivitiesProcessDefinitionXml(),
        eventSourceEntryDtos
      );
    String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);
    return eventProcessMappingId;
  }

  protected ExternalEventSourceEntryDto createExternalEventSource() {
    return EventProcessClient.createExternalEventAllGroupsSourceEntry();
  }

  protected CamundaEventSourceEntryDto createCamundaEventSourceEntryForDeployedProcessTracedByBusinessKey(
    final ProcessInstanceEngineDto processInstanceEngineDto) {
    return createCamundaEventSourceEntryForInstance(processInstanceEngineDto, null);
  }

  protected CamundaEventSourceEntryDto createCamundaEventSourceEntryForInstance(final ProcessInstanceEngineDto processInstanceEngineDto,
                                                                                final List<String> versions) {
    return createCamundaEventSourceEntryForInstance(processInstanceEngineDto, null, versions);
  }

  private CamundaEventSourceEntryDto createCamundaEventSourceEntryForInstance(
    final ProcessInstanceEngineDto processInstanceEngineDto,
    final String traceVariable,
    final List<String> versions) {
    return CamundaEventSourceEntryDto.builder()
      .configuration(CamundaEventSourceConfigDto.builder()
                       .eventScope(Collections.singletonList(EventScopeType.ALL))
                       .tracedByBusinessKey(traceVariable == null)
                       .traceVariable(traceVariable)
                       .versions(Optional.ofNullable(versions).orElse(Collections.singletonList("ALL")))
                       .processDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
                       .tenants(Collections.singletonList(processInstanceEngineDto.getTenantId()))
                       .build())
      .build();
  }

  protected ProcessInstanceDto createAndSaveEventInstanceContainingEvents(final List<CloudEventRequestDto> eventsToAdd,
                                                                          final String indexId) {
    final ProcessInstanceDto eventInstanceContainingEvent =
      eventProcessClient.createEventInstanceWithEvents(eventsToAdd);
    final EventProcessInstanceIndex eventInstanceIndex = createEventInstanceIndex(indexId);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      eventInstanceIndex.getIndexName(),
      IdGenerator.getNextId(),
      eventInstanceContainingEvent
    );
    return eventInstanceContainingEvent;
  }

  protected List<EventProcessInstanceDto> getAllStoredEventInstances() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getOptimizeIndexNameWithVersionWithWildcardSuffix(new EventProcessInstanceIndex("*")),
      EventProcessInstanceDto.class
    );
  }

  protected EventProcessInstanceDto getSavedInstanceWithId(final String processInstanceId) {
    final List<EventProcessInstanceDto> collect = getAllStoredEventInstances().stream()
      .filter(instance -> instance.getProcessInstanceId().equalsIgnoreCase(processInstanceId))
      .collect(Collectors.toList());
    assertThat(collect).hasSize(1);
    return collect.get(0);
  }

  protected List<EventDto> getAllStoredEvents() {
    return elasticSearchIntegrationTestExtension.getAllStoredExternalEvents();
  }

  @SneakyThrows
  protected EventProcessInstanceIndex createEventInstanceIndex(final String indexId) {
    final EventProcessInstanceIndex newIndex = new EventProcessInstanceIndex(indexId);
    final boolean indexExists = embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .indicesExist(embeddedOptimizeExtension.getOptimizeElasticClient(), Collections.singletonList(newIndex));
    if (!indexExists) {
      embeddedOptimizeExtension.getElasticSearchSchemaManager().createOrUpdateOptimizeIndex(
        embeddedOptimizeExtension.getOptimizeElasticClient(),
        newIndex
      );
    }
    return newIndex;
  }

  protected Map<String, EventMappingDto> createMappingsForEventProcess(final ProcessInstanceEngineDto processInstanceEngineDto,
                                                                       final String startEventName,
                                                                       final String intermediateEventName,
                                                                       final String endEventName) {
    final Map<String, EventMappingDto> eventMappings = new HashMap<>();
    eventMappings.put(BPMN_START_EVENT_ID, EventMappingDto.builder()
      .end(EventTypeDto.builder()
             .eventName(startEventName)
             .group(processInstanceEngineDto.getProcessDefinitionKey())
             .source(EVENT_SOURCE_CAMUNDA)
             .build())
      .build());
    eventMappings.put(USER_TASK_ID_ONE, EventMappingDto.builder()
      .start(EventTypeDto.builder()
               .eventName(intermediateEventName)
               .group(processInstanceEngineDto.getProcessDefinitionKey())
               .source(EVENT_SOURCE_CAMUNDA)
               .build())
      .build());
    eventMappings.put(BPMN_END_EVENT_ID, EventMappingDto.builder()
      .start(EventTypeDto.builder()
               .eventName(endEventName)
               .group(processInstanceEngineDto.getProcessDefinitionKey())
               .source(EVENT_SOURCE_CAMUNDA)
               .build())
      .build());
    return eventMappings;
  }

  protected void resetEventProcessInstanceImportProgress() {
    embeddedOptimizeExtension.getEventProcessInstanceImportMediatorManager()
      .getActiveMediators()
      .stream()
      .map(BackoffImportMediator::getImportIndexHandler)
      .map(o -> (TimestampBasedImportIndexHandler<?>) o)
      .forEach(TimestampBasedImportIndexHandler::resetImportIndex);
  }

  @SneakyThrows
  protected static String createSimpleProcessDefinitionXml() {
    return convertBpmnModelToXmlString(getSimpleBpmnDiagram());
  }

  @SneakyThrows
  protected static String createTwoEventAndOneTaskActivitiesProcessDefinitionXml() {
    return convertBpmnModelToXmlString(getSingleUserTaskDiagram(
      "aProcessName",
      BPMN_START_EVENT_ID,
      BPMN_END_EVENT_ID,
      USER_TASK_ID_ONE
    ));
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

  protected CamundaEventSourceEntryDto createCamundaSourceEntryForImportedDefinition(final String processName) {
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndImportInstanceWithProcessName(processName);
    return createCamundaEventSourceEntryForDeployedProcessTracedByBusinessKey(processInstanceEngineDto);
  }

  private ProcessInstanceEngineDto deployAndImportInstanceWithProcessName(final String processName) {
    final ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getSimpleBpmnDiagram(processName)
    );
    grantAuthorizationsToDefaultUser(processName);
    importAllEngineEntitiesFromScratch();
    return processInstanceEngineDto;
  }

  protected void grantAuthorizationsToDefaultUser(final String processDefinitionKey) {
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      DEFAULT_USERNAME,
      processDefinitionKey,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
  }

  @SuppressWarnings(UNUSED)
  protected static Stream<Arguments> exclusiveAndEventBasedGatewayXmlVariations() {
    return Stream.of(
      Arguments.of(EXCLUSIVE_GATEWAY_TYPE, EXCLUSIVE_GATEWAY_TYPE, createExclusiveGatewayProcessDefinitionXml()),
      Arguments.of(EVENT_BASED_GATEWAY_TYPE, EXCLUSIVE_GATEWAY_TYPE, createEventBasedGatewayProcessDefinitionXml())
    );
  }

  @SuppressWarnings(UNUSED)
  protected static Stream<List<EventSourceEntryDto<?>>> invalidExternalEventSourceCombinations() {
    return Stream.of(
      Arrays.asList(createExternalEventAllGroupsSourceEntry(), createExternalEventAllGroupsSourceEntry()),
      Arrays.asList(createExternalEventAllGroupsSourceEntry(), createExternalEventSourceEntryForGroup("aGroup")),
      Arrays.asList(createExternalEventSourceEntryForGroup("aGroup"), createExternalEventSourceEntryForGroup("aGroup")),
      Collections.singletonList(
        createExternalEventAllGroupsSourceEntry().toBuilder()
          .configuration(ExternalEventSourceConfigDto.builder().includeAllGroups(true).group("groupName").build())
          .build())
    );
  }

  protected boolean eventInstanceIndexForPublishStateExists(final EventProcessPublishStateDto publishState) {
    Map<String, List<AliasMetadata>> currentIndices = new HashMap<>();
    try {
      currentIndices = getEventProcessInstanceIndicesWithAliasesFromElasticsearch();
    } catch (ElasticsearchStatusException ex) {
      if (InstanceIndexUtil.isInstanceIndexNotFoundException(ex)) {
        return false;
      }
    }
    return currentIndices.containsKey(
      embeddedOptimizeExtension.getIndexNameService()
        .getOptimizeIndexNameWithVersion(new EventProcessInstanceIndex(publishState.getId())));
  }

  protected void publishMappingAndExecuteImport(final String eventProcessId) {
    eventProcessClient.publishEventProcessMapping(eventProcessId);
    // we execute the import cycle so the event instance index gets created
    executeImportCycle();
  }

  protected EventProcessPublishStateDto getEventProcessPublishStateDto(final String processMappingId) {
    return getEventProcessPublishStateDtoFromElasticsearch(processMappingId)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Could not fetch publish state for process mapping"));
  }

}
