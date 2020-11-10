/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
import com.fasterxml.jackson.databind.JsonNode
import lombok.SneakyThrows
import org.camunda.optimize.OptimizeRequestExecutor
import org.camunda.optimize.dto.optimize.ReportConstants
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto
import org.camunda.optimize.dto.optimize.query.entity.EntityType
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto
import org.camunda.optimize.service.es.OptimizeElasticsearchClient
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder
import org.camunda.optimize.test.optimize.*
import org.camunda.optimize.test.util.ProcessReportDataType
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest
import org.elasticsearch.client.RequestOptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import javax.ws.rs.core.Response
import java.util.function.Function
import java.util.stream.Collectors

import static org.assertj.core.api.Assertions.assertThat

class PostMigrationTest {
  private static final String DEFAULT_USER = "demo";

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
      .allSatisfy(alertDefinitionDto -> assertThat(alertDefinitionDto).isNotNull());
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
        .filter(entityDto -> EntityType.REPORT.equals(entityDto.getEntityType()))
        .collect(Collectors.toList())) {
        final Response response = requestExecutor.buildEvaluateSavedReportRequest(entity.getId())
          .execute(Response.Status.OK.getStatusCode());
        final JsonNode jsonResponse = response.readEntity(JsonNode.class);
        assertThat(jsonResponse.hasNonNull(AuthorizedEvaluationResultDto.Fields.result.name())).isTrue();
      }
    }
  }

  @Test
  void retrieveAllEventBasedProcessesAndEnsureTheyArePublishedAndHaveInstanceData() {
    final List<EventProcessMappingDto> allEventProcessMappings = eventProcessClient.getAllEventProcessMappings();
    assertEventProcessesArePublished(allEventProcessMappings);

    refreshAllElasticsearchIndices();

    final Map<String, Long> eventProcessInstanceCounts = retrieveEventProcessInstanceCounts(allEventProcessMappings);
    assertThat(eventProcessInstanceCounts.entrySet())
      .isNotEmpty()
      .allSatisfy(entry -> {
        assertThat(entry.getValue())
          .withFailMessage("Event process with key %s did not contain instances.", entry.getKey())
          .isGreaterThan(0L);
      });
  }

  @Test
  void republishAllEventBasedProcessesAndEnsureTheyArePublishedAndHaveInstanceData() {
    final List<EventProcessMappingDto> eventProcessMappingsBeforeRepublish =
      eventProcessClient.getAllEventProcessMappings();
    assertThat(eventProcessMappingsBeforeRepublish).isNotEmpty();

    final Map<String, Long> eventProcessInstanceCountsBeforeRepublish =
      retrieveEventProcessInstanceCounts(eventProcessMappingsBeforeRepublish);

    eventProcessMappingsBeforeRepublish.forEach(eventProcessMappingDto -> {
      final String currentEventProcessMappingId = eventProcessMappingDto.getId();
      // update it to allow another publish (but no actual changes required)
      // we need to fetch the xml as it's not included in the list results
      eventProcessMappingDto.setXml(eventProcessClient.getEventProcessMapping(eventProcessMappingDto.getId()).getXml());
      eventProcessClient.updateEventProcessMapping(currentEventProcessMappingId, eventProcessMappingDto);
      eventProcessClient.publishEventProcessMapping(currentEventProcessMappingId);
      eventProcessClient.waitForEventProcessPublish(currentEventProcessMappingId);
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
    elasticsearchClient.getHighLevelClient().indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
  }

  private static void assertEventProcessesArePublished(final List<EventProcessMappingDto> allEventProcessMappings) {
    assertThat(allEventProcessMappings)
      .isNotEmpty()
      .extracting((Function<EventProcessMappingDto, EventProcessState>) EventProcessMappingDto::getState)
      .allSatisfy(eventProcessState -> assertThat(eventProcessState).isEqualTo(EventProcessState.PUBLISHED));
  }

  private static AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateRawDataReportForProcessKey(
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
      .filter(entityDto -> EntityType.COLLECTION.equals(entityDto.getEntityType()))
      .collect(Collectors.toList());
  }
}
