/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.upgrade;

import static io.camunda.optimize.service.tenant.CamundaPlatformTenantService.TENANT_NOT_DEFINED;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import io.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit;
import io.camunda.optimize.dto.optimize.query.alert.AlertThresholdOperator;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DimensionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.PositionDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import io.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.ProcessReportDataType;
import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import io.camunda.optimize.test.optimize.AlertClient;
import io.camunda.optimize.test.optimize.CollectionClient;
import io.camunda.optimize.test.optimize.DashboardClient;
import io.camunda.optimize.test.optimize.EventProcessClient;
import io.camunda.optimize.test.optimize.IngestionClient;
import io.camunda.optimize.test.optimize.ReportClient;
import io.camunda.optimize.test.util.ReportsGenerator;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import io.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;

@Slf4j
public class Generator {

  private static final String DEFAULT_USER = "demo";
  private static final String API_SECRET = "secret";

  private final OptimizeElasticsearchClient elasticsearchClient;
  private final SimpleEngineClient client =
      new SimpleEngineClient(IntegrationTestConfigurationUtil.getEngineRestEndpoint() + "default");

  private final CollectionClient collectionClient;
  private final ReportClient reportClient;
  private final AlertClient alertClient;
  private final DashboardClient dashboardClient;
  private final IngestionClient ingestionClient;
  private final EventProcessClient eventProcessClient;

  public Generator() {
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    elasticsearchClient =
        new OptimizeElasticsearchClient(
            ElasticsearchHighLevelRestClientBuilder.build(configurationService),
            new OptimizeIndexNameService(configurationService, DatabaseType.ELASTICSEARCH),
            OPTIMIZE_MAPPER);

    final OptimizeRequestExecutor requestExecutor =
        new OptimizeRequestExecutor(DEFAULT_USER, DEFAULT_USER, "http://localhost:8090/api/");

    collectionClient = new CollectionClient(() -> requestExecutor);
    reportClient = new ReportClient(() -> requestExecutor);
    alertClient = new AlertClient(() -> requestExecutor);
    dashboardClient = new DashboardClient(() -> requestExecutor);
    ingestionClient = new IngestionClient(() -> requestExecutor, () -> API_SECRET);
    eventProcessClient = new EventProcessClient(() -> requestExecutor);
  }

  public static void main(final String[] args) throws InterruptedException, IOException {
    final Generator generator = new Generator();
    try {
      generator.ingestExternalEvents();
      // sleeping to ensure event sequence count processing has been performed
      Thread.sleep(30_000L);

      final ProcessDefinitionEngineDto engineProcessDefinition =
          generator.getDefaultEngineProcessDefinition();
      final DecisionDefinitionEngineDto engineDecisionDefinition =
          generator.getDefaultEngineDecisionDefinition();
      generator.generateCollectionWithAllReportsSomeDashboardsAndOneAlert(
          engineProcessDefinition, engineDecisionDefinition);

      final String camundaEventProcessKey =
          generator.createAndPublishCamundaEventProcess(engineProcessDefinition);
      generator.generateCollectionWithSomeReportsOnProcess(camundaEventProcessKey);
      final String externalEventProcessKey = generator.createAndPublishExternalEventProcess();
      generator.generateCollectionWithSomeReportsOnProcess(externalEventProcessKey);
    } finally {
      generator.shutdown();
    }
  }

  private void shutdown() {
    try {
      log.info("Closing highlevel client.");
      elasticsearchClient.close();
      log.info("Closing lowlevel client.");
      elasticsearchClient.getLowLevelClient().close();
      log.info("Shutting down generator.");
      System.exit(0);
    } catch (final Exception e) {
      log.info("Exception while closing ES client", e);
      System.exit(-1);
    }
  }

  private ProcessDefinitionEngineDto getDefaultEngineProcessDefinition() {
    return client.getLatestProcessDefinitions().get(0);
  }

  private DecisionDefinitionEngineDto getDefaultEngineDecisionDefinition() {
    return client.getLatestDecisionDefinitions().get(0);
  }

  private void generateCollectionWithAllReportsSomeDashboardsAndOneAlert(
      final ProcessDefinitionEngineDto processDefinition,
      final DecisionDefinitionEngineDto decisionDefinition) {
    log.info(
        "Generating new collection with reports, dashboards and one alert for processDefinition {} and decisionDefinition {}.",
        processDefinition.getKey(),
        decisionDefinition.getKey());
    final String collectionId = collectionClient.createNewCollection();
    addScopeToCollection(collectionId, processDefinition.getKey(), DefinitionType.PROCESS);
    addScopeToCollection(collectionId, decisionDefinition.getKey(), DefinitionType.DECISION);

    final List<String> engineReportIds =
        generateReportsInCollection(processDefinition, decisionDefinition, collectionId);
    generateDashboard(engineReportIds.subList(0, 10), collectionId);
    // empty dashboard
    dashboardClient.createDashboard(collectionId, Collections.emptyList());
    generateAlert(processDefinition.getKey(), collectionId);
    log.info("Collection with ID {} generated.", collectionId);
  }

  private void generateCollectionWithSomeReportsOnProcess(final String definitionKey) {
    log.info("Generating collection with reports on process {}.", definitionKey);
    final String collectionId = collectionClient.createNewCollection();
    addScopeToCollection(collectionId, definitionKey, DefinitionType.PROCESS);

    final String collectionReport1 =
        createSingleNumberReportInCollection(collectionId, definitionKey);
    final String collectionReport2 =
        createSingleNumberReportInCollection(collectionId, definitionKey);

    generateDashboard(ImmutableList.of(collectionReport1, collectionReport2), collectionId);
    log.info("Collection with ID {} generated.", collectionId);
  }

  private void generateAlert(final String definitionKey, final String collectionId) {
    log.info("Generate alert on definition {} in collection {}.", definitionKey, collectionId);
    final String collectionNumberReportId =
        createSingleNumberReportInCollection(collectionId, definitionKey);
    final AlertCreationRequestDto alertCreation = prepareAlertCreation(collectionNumberReportId);
    alertClient.createAlert(alertCreation);
    log.info("Done generating alert.");
  }

  private void addScopeToCollection(
      final String collectionId, final String definitionKey, final DefinitionType definitionType) {
    log.info("Adding scope for definition {} to collection {}.", definitionKey, collectionId);
    final List<String> tenants = new ArrayList<>();
    tenants.add(null);
    collectionClient.addScopeEntryToCollection(
        collectionId, new CollectionScopeEntryDto(definitionType, definitionKey, tenants));
    log.info("Done adding scope to collection {}.", collectionId);
  }

  private void generateDashboard(final List<String> reportIds, final String collectionId) {
    log.info("Generating dashboard in collection {}.", collectionId);
    dashboardClient.createDashboard(prepareDashboard(reportIds, collectionId));
    log.info("Done generating dashboard in collection {}.", collectionId);
  }

  private String createSingleNumberReportInCollection(
      final String collectionId, final String definitionKey) {
    log.info("Creating report in collection {}.", collectionId);
    final ProcessReportDataDto reportData =
        TemplatedProcessReportDataBuilder.createReportData()
            .setProcessDefinitionKey(definitionKey)
            .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
            .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
            .build();
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
        new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    singleProcessReportDefinitionDto.setData(reportData);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private List<String> generateReportsInCollection(
      final ProcessDefinitionEngineDto processDefinition,
      final DecisionDefinitionEngineDto decisionDefinition,
      final String collectionId) {
    log.info("Generating reports in collection {}.", collectionId);
    final List<String> reportIds = new ArrayList<>();

    final ProcessReportDataDto combinableProcessBarReport =
        TemplatedProcessReportDataBuilder.createReportData()
            .setProcessDefinitionKey(processDefinition.getKey())
            .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
            .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE)
            .setGroupByDateInterval(AggregateByDateUnit.DAY)
            .setVisualization(ProcessVisualization.BAR)
            .build();
    // here we want two of the same type to be combined in a combined report to follow
    final String combinableReport1 = createProcessReport(combinableProcessBarReport, collectionId);
    reportIds.add(combinableReport1);
    final String combinableReport2 = createProcessReport(combinableProcessBarReport, collectionId);
    reportIds.add(combinableReport2);
    reportIds.add(
        reportClient.createCombinedReport(
            collectionId, Lists.newArrayList(combinableReport1, combinableReport2)));

    final List<String> generatedReports =
        ReportsGenerator.createAllReportTypesForDefinitions(
                Collections.singletonList(processDefinition),
                Collections.singletonList(decisionDefinition))
            .stream()
            .map(
                r -> {
                  if (r instanceof DecisionReportDataDto) {
                    return createDecisionReport((DecisionReportDataDto) r, collectionId);
                  }
                  if (r instanceof ProcessReportDataDto) {
                    return createProcessReport((ProcessReportDataDto) r, collectionId);
                  }
                  throw new OptimizeRuntimeException(
                      "Unknown object type provided from the ReportsGenerator");
                })
            .collect(Collectors.toList());

    reportIds.addAll(generatedReports);
    log.info("Done generating reports in collection {}.", collectionId);
    return reportIds;
  }

  private String createProcessReport(
      final ProcessReportDataDto reportData, final String collectionId) {
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
        new SingleProcessReportDefinitionRequestDto(reportData);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private String createDecisionReport(
      final DecisionReportDataDto reportData, final String collectionId) {
    final SingleDecisionReportDefinitionRequestDto decisionReportDefinition =
        new SingleDecisionReportDefinitionRequestDto(reportData);
    decisionReportDefinition.setCollectionId(collectionId);
    return reportClient.createSingleDecisionReport(decisionReportDefinition);
  }

  private void ingestExternalEvents() {
    final List<CloudEventRequestDto> cloudEvents =
        IntStream.range(0, 10)
            .mapToObj(
                traceId ->
                    Lists.newArrayList(
                        ingestionClient.createCloudEventDto().toBuilder()
                            .source("dataMigration")
                            .group("test")
                            .type("start")
                            .traceid(String.valueOf(traceId))
                            .build(),
                        ingestionClient.createCloudEventDto().toBuilder()
                            .source("dataMigration")
                            .group("test")
                            .type("end")
                            .traceid(String.valueOf(traceId))
                            .build()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    ingestionClient.ingestEventBatch(cloudEvents);
    refreshElasticSearch();
  }

  @SneakyThrows
  private void refreshElasticSearch() {
    elasticsearchClient.refresh(new RefreshRequest("*"));
  }

  private String createAndPublishExternalEventProcess() {
    final ExternalEventSourceEntryDto externalEventSourceEntryDto =
        ExternalEventSourceEntryDto.builder()
            .configuration(
                ExternalEventSourceConfigDto.builder()
                    .eventScope(Collections.singletonList(EventScopeType.ALL))
                    .includeAllGroups(true)
                    .group(null)
                    .build())
            .build();
    return createAndPublishAutoGeneratedEventProcessWithSource(externalEventSourceEntryDto);
  }

  private String createAndPublishCamundaEventProcess(
      final ProcessDefinitionEngineDto definitionEngineDto) {
    final CamundaEventSourceEntryDto camundaEventSourceEntryDto =
        CamundaEventSourceEntryDto.builder()
            .configuration(
                CamundaEventSourceConfigDto.builder()
                    .eventScope(Collections.singletonList(EventScopeType.START_END))
                    .processDefinitionKey(definitionEngineDto.getKey())
                    .versions(Collections.singletonList(definitionEngineDto.getVersionAsString()))
                    .tenants(
                        Collections.singletonList(
                            definitionEngineDto.getTenantId().orElse(TENANT_NOT_DEFINED.getId())))
                    .tracedByBusinessKey(true)
                    .traceVariable(null)
                    .build())
            .build();
    return createAndPublishAutoGeneratedEventProcessWithSource(camundaEventSourceEntryDto);
  }

  private String createAndPublishAutoGeneratedEventProcessWithSource(
      final EventSourceEntryDto<?> eventSourceEntryDto) {
    final EventProcessMappingCreateRequestDto createRequestDto =
        EventProcessMappingCreateRequestDto.eventProcessMappingCreateBuilder()
            .autogenerate(true)
            .eventSources(Collections.singletonList(eventSourceEntryDto))
            .build();
    final String eventProcessMappingId =
        eventProcessClient.createEventProcessMapping(createRequestDto);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    eventProcessClient.waitForEventProcessPublish(eventProcessMappingId);

    return eventProcessMappingId;
  }

  private static AlertCreationRequestDto prepareAlertCreation(final String id) {
    final AlertCreationRequestDto alertCreation = new AlertCreationRequestDto();

    alertCreation.setReportId(id);
    alertCreation.setThreshold(700.0);
    alertCreation.setEmails(Collections.singletonList("foo@gmail.bar"));
    alertCreation.setName("alertFoo");
    alertCreation.setThresholdOperator(AlertThresholdOperator.LESS);
    alertCreation.setFixNotification(true);

    final AlertInterval interval = new AlertInterval();
    interval.setValue(17);
    interval.setUnit(AlertIntervalUnit.MINUTES);

    alertCreation.setCheckInterval(interval);
    alertCreation.setReminder(interval);

    return alertCreation;
  }

  private static DashboardDefinitionRestDto prepareDashboard(
      final List<String> reportIds, final String collectionId) {
    final List<DashboardReportTileDto> reportLocations =
        reportIds.stream()
            .map(
                reportId -> {
                  final DashboardReportTileDto report = new DashboardReportTileDto();
                  report.setId(reportId);
                  report.setType(DashboardTileType.OPTIMIZE_REPORT);

                  final PositionDto position = new PositionDto();
                  position.setX((reportIds.indexOf(reportId) % 3) * 6);
                  position.setY((reportIds.indexOf(reportId) / 3) * 4);
                  report.setPosition(position);

                  final DimensionDto dimensions = new DimensionDto();
                  dimensions.setHeight(4);
                  dimensions.setWidth(6);
                  report.setDimensions(dimensions);

                  return report;
                })
            .collect(Collectors.toList());

    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setCollectionId(collectionId);
    dashboard.setTiles(reportLocations);

    return dashboard;
  }
}
