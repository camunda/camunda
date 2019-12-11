/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventProcessPublishStateDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.util.IdGenerator;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class AbstractEventProcessIT extends AbstractIT {
  protected static final String MY_TRACE_ID_1 = "myTraceId1";

  protected static final String BPMN_START_EVENT_ID = "StartEvent_1";
  protected static final String BPMN_END_EVENT_ID = "EndEvent_2";
  protected static final String VARIABLE_ID = "var";
  protected static final String VARIABLE_VALUE = "value";
  protected static final String EVENT_GROUP = "test";
  protected static final String EVENT_SOURCE = "integrationTest";
  public static final String EVENT_PROCESS_NAME = "myEventProcess";

  protected String createSimpleEventProcessMapping(final String ingestedStartEventName,
                                                   final String ingestedEndEventName) {
    EventProcessMappingDto eventProcessMappingDto = createSimpleEventProcessMappingDto(
      ingestedStartEventName,
      ingestedEndEventName
    );
    return eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
  }

  protected EventProcessMappingDto createSimpleEventProcessMappingDto(final String ingestedStartEventName,
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
    return eventProcessClient.createEventProcessMappingDtoWithMappingsWithXml(
      eventMappings, EVENT_PROCESS_NAME, createSimpleProcessDefinitionXml()
    );
  }

  @SneakyThrows
  protected void executeImportCycle() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getIngestedEventImportScheduler()
      .runImportCycle()
      .get(10, TimeUnit.SECONDS);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @SneakyThrows
  protected Map<String, List<String>> getEventProcessInstanceIndicesWithAliasesFromElasticsearch() {
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
      .collect(Collectors.toMap(
        cursor -> cursor.key,
        cursor -> cursor.value.stream().map(AliasMetaData::alias).collect(Collectors.toList())
      ));
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
    if (searchResponse.getHits().totalHits > 0) {
      result = elasticSearchIntegrationTestExtension.getObjectMapper().readValue(
        searchResponse.getHits().getAt(0).getSourceAsString(),
        IndexableEventProcessPublishStateDto.class
      ).toEventProcessPublishStateDto();
    }

    return Optional.ofNullable(result);
  }

  @SneakyThrows
  protected Optional<EventProcessDefinitionDto> getEventProcessDefinitionFromElasticsearch(
    final String definitionId) {
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
    final List<ProcessInstanceDto> results = new ArrayList<>();
    final SearchResponse searchResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .search(
        new SearchRequest(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + "*"),
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

  protected String ingestTestEvent(final String eventId, final String event, final OffsetDateTime eventTimestamp) {
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

  protected String getEventPublishStateIdForEventProcessMappingId(final String eventProcessMappingId) {
    return getEventProcessPublishStateDtoFromElasticsearch(eventProcessMappingId)
      .map(EventProcessPublishStateDto::getId)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Could not get id of published process"));
  }
}
