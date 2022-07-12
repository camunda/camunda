/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package com.camunda.optimize.test.upgrade

import com.fasterxml.jackson.databind.JsonNode
import lombok.SneakyThrows
import org.camunda.optimize.OptimizeRequestExecutor
import org.camunda.optimize.dto.optimize.ReportConstants
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto
import org.camunda.optimize.dto.optimize.query.entity.EntityType
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState
import org.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto
import org.camunda.optimize.service.es.OptimizeElasticsearchClient
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex
import org.camunda.optimize.service.exceptions.evaluation.TooManyBucketsException
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder
import org.camunda.optimize.test.optimize.AlertClient
import org.camunda.optimize.test.optimize.CollectionClient
import org.camunda.optimize.test.optimize.EntitiesClient
import org.camunda.optimize.test.optimize.EventProcessClient
import org.camunda.optimize.test.optimize.ReportClient
import org.camunda.optimize.service.util.ProcessReportDataType
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.ws.rs.core.Response
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.fail
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME

class PostMigrationTest {
  private static final String DEFAULT_USER = "demo";
  private static final Logger log = LoggerFactory.getLogger(PostMigrationTest.class);

  private static OptimizeRequestExecutor requestExecutor;
  private static OptimizeElasticsearchClient elasticsearchClient;
  private static AlertClient alertClient;
  private static CollectionClient collectionClient;
  private static EntitiesClient entitiesClient;
  private static EventProcessClient eventProcessClient;
  private static ReportClient reportClient;

  @BeforeAll
  static void init() {
    requestExecutor = new OptimizeRequestExecutor(DEFAULT_USER, DEFAULT_USER, "http://localhost:8090/api/");
    def configurationService = ConfigurationServiceBuilder.createDefaultConfiguration()
    elasticsearchClient = new OptimizeElasticsearchClient(
      ElasticsearchHighLevelRestClientBuilder.build(configurationService),
      new OptimizeIndexNameService(configurationService)
    );

    alertClient = new AlertClient(() -> requestExecutor);
    collectionClient = new CollectionClient(() -> requestExecutor);
    entitiesClient = new EntitiesClient(() -> requestExecutor);
    eventProcessClient = new EventProcessClient(() -> requestExecutor);
    reportClient = new ReportClient(() -> requestExecutor);
  }

  @Test
  void retrieveAllEntities() {
    final List<EntityResponseDto> entities = entitiesClient.getAllEntities();
    assertThat(entities).isNotEmpty();
  }

  @Test
  void retrieveAlerts() {
    List<AlertDefinitionDto> allAlerts = new ArrayList<>();

    List<EntityResponseDto> collections = getCollections();
    collections.forEach(collection -> {
      allAlerts.addAll(alertClient.getAlertsForCollectionAsDefaultUser(collection.getId()));
    });

    assertThat(allAlerts)
      .isNotEmpty()
      .allSatisfy((alertDefinitionDto -> assertThat(alertDefinitionDto).isNotNull()) as Consumer<AlertDefinitionDto>);
  }

  @Test
  void retrieveAllCollections() {
    final List<EntityResponseDto> collections = getCollections();

    assertThat(collections).isNotEmpty();
    for (EntityResponseDto collection : collections) {
      assertThat(collectionClient.getCollectionById(collection.getId())).isNotNull();
    }
  }

  @Test
  void evaluateAllCollectionReports() {
    final List<EntityResponseDto> collections = getCollections();
    for (EntityResponseDto collection : collections) {
      final List<EntityResponseDto> collectionEntities = collectionClient.getEntitiesForCollection(collection.getId());
      for (EntityResponseDto entity : collectionEntities.stream()
        .filter(entityDto -> EntityType.REPORT == entityDto.getEntityType())
        .collect(Collectors.toList())) {
        final long startMillis = System.currentTimeMillis();
        Response response = null;
        try {
          response = requestExecutor.buildEvaluateSavedReportRequest(entity.getId()).execute();
        } finally {
          log.info(
            "Evaluation of reportId: {} with commandKey: {} took: {}ms",
            entity.getId(),
            reportClient.getReportById(entity.getId()).getData().createCommandKey(),
            System.currentTimeMillis() - startMillis
          );
        }
        if (response != null) {
          final JsonNode jsonResponse = response.readEntity(JsonNode.class);
          if (Response.Status.OK.getStatusCode() == response.getStatus()) {
            assertThat(jsonResponse.hasNonNull(AuthorizedSingleReportEvaluationResponseDto.Fields.result.name())).isTrue();
          } else if (Response.Status.BAD_REQUEST.getStatusCode() == response.getStatus()
            && jsonResponse.get(ErrorResponseDto.Fields.errorCode).asText() == TooManyBucketsException.ERROR_CODE) {
            assertThat(jsonResponse.get(ErrorResponseDto.Fields.errorCode).asText())
              .isEqualTo(TooManyBucketsException.ERROR_CODE);
            log.warn("Encountered too many buckets for reportId: {}", entity.getId());
          } else {
            fail(
              "Report evaluation failed with status code ${response.status} and body: ${response.readEntity(String.class)}."
            )
          }
        }
      }
    }
  }

  @Test
  void retrieveAllEventBasedProcessesAndEnsureTheyArePublishedAndHaveInstanceData() {
    final List<EventProcessMappingDto> allEventProcessMappings = eventProcessClient.getAllEventProcessMappings();
    assertThat(allEventProcessMappings).hasSize(2);
    assertEventProcessesArePublished(allEventProcessMappings);

    refreshAllElasticsearchIndices();

    final Map<String, Long> eventProcessInstanceCounts = retrieveEventProcessInstanceCounts(allEventProcessMappings);
    assertThat(eventProcessInstanceCounts.entrySet())
      .isNotEmpty()
      .allSatisfy((Map.Entry<String, Long> entry) -> {
        assertThat(entry.getValue())
          .withFailMessage("Event process with key %s did not contain instances.", entry.getKey())
          .isGreaterThan(0L)
      } as Consumer<Map.Entry<String, Long>>)
  }

  @Test
  void republishAllEventBasedProcessesAndEnsureTheyArePublishedAndHaveInstanceData() {
    final List<EventProcessMappingDto> eventProcessMappingsBeforeRepublish =
      eventProcessClient.getAllEventProcessMappings();
    assertThat(eventProcessMappingsBeforeRepublish).hasSize(2);
    assertEventProcessesArePublished(eventProcessMappingsBeforeRepublish);

    final Map<String, Long> eventProcessInstanceCountsBeforeRepublish =
      retrieveEventProcessInstanceCounts(eventProcessMappingsBeforeRepublish);

    assertThat(eventProcessInstanceCountsBeforeRepublish.values()).doesNotContain(0L);

    eventProcessMappingsBeforeRepublish.forEach(eventProcessMappingDto -> {
      final String currentEventProcessMappingId = eventProcessMappingDto.getId();
      // update it to allow another publish (but no actual changes required)
      // we need to fetch the xml as it's not included in the list results
      eventProcessMappingDto.setXml(eventProcessClient.getEventProcessMapping(eventProcessMappingDto.getId()).getXml());
      eventProcessClient.updateEventProcessMapping(currentEventProcessMappingId, eventProcessMappingDto);
      eventProcessClient.publishEventProcessMapping(currentEventProcessMappingId);
      eventProcessClient.waitForEventProcessPublish(currentEventProcessMappingId);
      waitForOldIndexToBeCleanedUp(currentEventProcessMappingId);
    });

    final List<EventProcessMappingDto> republishedEventProcessMappings =
      eventProcessClient.getAllEventProcessMappings();
    assertThat(republishedEventProcessMappings).hasSameSizeAs(eventProcessMappingsBeforeRepublish);
    assertEventProcessesArePublished(republishedEventProcessMappings);

    refreshAllElasticsearchIndices();

    final Map<String, Long> eventProcessInstanceCountsAfterRepublish =
      retrieveEventProcessInstanceCounts(republishedEventProcessMappings);

    assertThat(eventProcessInstanceCountsAfterRepublish).isEqualTo(eventProcessInstanceCountsBeforeRepublish);
  }

  private static Map<String, Long> retrieveEventProcessInstanceCounts(final List<EventProcessMappingDto> eventProcessMappings) {
    return eventProcessMappings.stream()
      .map(EventProcessMappingDto::getId)
      .map(this::evaluateRawDataReportForProcessKey)
      .collect(Collectors.toMap(
        report -> report.getReportDefinition().getData().getProcessDefinitionKey(),
        report -> report.getResult().getInstanceCount()
      ));
  }

  @SneakyThrows
  private static void refreshAllElasticsearchIndices() {
    elasticsearchClient.refresh(new RefreshRequest("*"));
  }

  private static void assertEventProcessesArePublished(final List<EventProcessMappingDto> allEventProcessMappings) {
    assertThat(allEventProcessMappings)
      .isNotEmpty()
      .extracting((Function<EventProcessMappingDto, EventProcessState>) EventProcessMappingDto::getState)
      .allSatisfy((eventProcessState -> assertThat(eventProcessState == EventProcessState.PUBLISHED)) as Consumer<EventProcessState>);
  }

  private static void waitForOldIndexToBeCleanedUp(final String processMappingId) {
    final SearchResponse searchResponse = elasticsearchClient.search(
      new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME).source(new SearchSourceBuilder().size(10000)));
    List<String> eventIndicesForMapping = Arrays.stream(searchResponse.getHits().getHits())
      .map(hit -> hit.getSourceAsMap())
      .filter(publishState -> processMappingId.equals(publishState.get(EventProcessPublishStateDto.Fields.processMappingId)))
      .map(publishState -> (String) publishState.get(EventProcessPublishStateDto.Fields.id))
      .map(publishStateId -> new EventProcessInstanceIndex(publishStateId).getIndexName())
      .collect(Collectors.toList());
    boolean singleIndexExists = false;
    while (!singleIndexExists) {
      def indexCount = eventIndicesForMapping.stream()
        .filter(indexName -> elasticsearchClient.exists(indexName))
        .count();
      log.info("There are {} Event Process Instance Indices for process mapping with ID {}. Index names: {}",
        indexCount, processMappingId, eventIndicesForMapping);
      singleIndexExists = indexCount == 1;
      Thread.sleep(5000L);
    }
  }

  private static AuthorizedProcessReportEvaluationResponseDto<List<RawDataInstanceDto>> evaluateRawDataReportForProcessKey(
    final String eventProcessKey) {
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(eventProcessKey)
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    return reportClient.evaluateRawReport(reportData);
  }

  private static List<EntityResponseDto> getCollections() {
    final List<EntityResponseDto> entities = entitiesClient.getAllEntities();

    return entities.stream()
      .filter(entityDto -> EntityType.COLLECTION == entityDto.getEntityType())
      .collect(Collectors.toList());
  }

}
