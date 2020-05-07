/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.upgrade;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.test.optimize.AlertClient;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.camunda.optimize.test.optimize.DashboardClient;
import org.camunda.optimize.test.optimize.ReportClient;
import org.camunda.optimize.test.util.ReportsGenerator;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Generator {

  public static final String DEFAULT_USER = "demo";
  private String processDefinitionKey;
  private String processDefinitionVersion;

  private final CollectionClient collectionClient;
  private final ReportClient reportClient;
  private final AlertClient alertClient;
  private final DashboardClient dashboardClient;

  public Generator() {
    WebTarget optimizeClient = ClientBuilder.newClient().target("http://localhost:8090/api/");
    final OptimizeRequestExecutor requestExecutor =
      new OptimizeRequestExecutor(
        optimizeClient,
        new ObjectMapperFactory(
          new OptimizeDateTimeFormatterFactory().getObject(),
          ConfigurationServiceBuilder.createDefaultConfiguration()
        ).createOptimizeMapper()
      ).withUserAuthentication(DEFAULT_USER, DEFAULT_USER).withCurrentUserAuthenticationAsNewDefaultToken();
    collectionClient = new CollectionClient(() -> requestExecutor);
    reportClient = new ReportClient(() -> requestExecutor);
    alertClient = new AlertClient(() -> requestExecutor);
    dashboardClient = new DashboardClient(() -> requestExecutor);
  }

  public static void main(String[] args) {
    final Generator generator = new Generator();
    generator.setDefaultProcessDefinition();

    List<String> reportIds = generator.generateReports();
    generator.generateAlerts();
    generator.generateDashboards(reportIds);
    generator.generateCollection();
  }

  private void setDefaultProcessDefinition() {
    ProcessDefinitionEngineDto processDefinitionEngineDto = getEngineProcessDefinitions().get(0);
    processDefinitionKey = processDefinitionEngineDto.getKey();
    processDefinitionVersion = processDefinitionEngineDto.getVersionAsString();
  }

  private void generateAlerts() {
    final String collectionId = collectionClient.createNewCollection();
    addScopeToCollection(collectionId);
    final String collectionNumberReportId = createSingleNumberReportInCollection(collectionId);
    AlertCreationDto alertCreation = prepareAlertCreation(collectionNumberReportId);
    alertClient.createAlert(alertCreation);
  }

  private void addScopeToCollection(final String collectionId) {
    final List<String> tenants = new ArrayList<>();
    tenants.add(null);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(DefinitionType.PROCESS, processDefinitionKey, tenants)
    );
  }

  private void generateDashboards(List<String> reportIds) {
    dashboardClient.createDashboard(prepareDashboard(reportIds));
    dashboardClient.createDashboard(null, Collections.emptyList());
  }

  private void generateCollection() {
    final String collectionId = collectionClient.createNewCollection();
    addScopeToCollection(collectionId);

    final String collectionReport1 = createSingleNumberReportInCollection(collectionId);
    final String collectionReport2 = createSingleNumberReportInCollection(collectionId);

    dashboardClient.createDashboard(collectionId, Lists.newArrayList(collectionReport1, collectionReport2));
  }

  private String createSingleNumberReportInCollection(final String collectionId) {
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    singleProcessReportDefinitionDto.setData(reportData);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private List<String> generateReports() {
    final List<String> reportIds = new ArrayList<>();

    final ProcessReportDataDto combinableProcessBarReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE)
      .setDateInterval(GroupByDateUnit.DAY)
      .setVisualization(ProcessVisualization.BAR)
      .build();
    // here we want two of the same type to be combined in a combined report to follow
    final String combinableReport1 = createProcessReport(combinableProcessBarReport);
    reportIds.add(combinableReport1);
    final String combinableReport2 = createProcessReport(combinableProcessBarReport);
    reportIds.add(combinableReport2);

    reportIds.add(
      reportClient.createCombinedReport(
        null, Lists.newArrayList(combinableReport1, combinableReport2)
      )
    );

    List<String> generatedReports = ReportsGenerator.createAllPossibleReports().stream().map(r -> {
      if (r instanceof DecisionReportDataDto) {
        return createDecisionReport((DecisionReportDataDto) r);
      }
      if (r instanceof ProcessReportDataDto) {
        return createProcessReport((ProcessReportDataDto) r);
      }
      throw new OptimizeRuntimeException("Unknown object type provided from the ReportsGenerator");

    }).collect(Collectors.toList());

    reportIds.addAll(generatedReports);

    return reportIds;
  }

  private String createProcessReport(final ProcessReportDataDto reportData) {
    return reportClient.createSingleProcessReport(new SingleProcessReportDefinitionDto(reportData));
  }

  private String createDecisionReport(final DecisionReportDataDto reportData) {
    return reportClient.createSingleDecisionReport(new SingleDecisionReportDefinitionDto(reportData));
  }

  private static List<ProcessDefinitionEngineDto> getEngineProcessDefinitions() {
    final Client client = ClientBuilder.newClient().register(
      new OptimizeObjectMapperContextResolver(new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
    );
    try {
      final WebTarget target = client.target("http://localhost:8080/engine-rest/process-definition");
      Response response = target.request().get();

      // @formatter:off
      return response.readEntity(new GenericType<List<ProcessDefinitionEngineDto>>() {});
      // @formatter:on
    } finally {
      client.close();
    }
  }

  private static AlertCreationDto prepareAlertCreation(String id) {
    AlertCreationDto alertCreation = new AlertCreationDto();

    alertCreation.setReportId(id);
    alertCreation.setThreshold(700L);
    alertCreation.setEmail("foo@gmail.bar");
    alertCreation.setName("alertFoo");
    alertCreation.setThresholdOperator("<");
    alertCreation.setFixNotification(true);

    AlertInterval interval = new AlertInterval();
    interval.setValue(17);
    interval.setUnit("Minutes");

    alertCreation.setCheckInterval(interval);
    alertCreation.setReminder(interval);

    return alertCreation;
  }

  private static DashboardDefinitionDto prepareDashboard(List<String> reportIds) {
    List<ReportLocationDto> reportLocations = reportIds.stream().map(reportId -> {
      ReportLocationDto report = new ReportLocationDto();
      report.setId(reportId);

      PositionDto position = new PositionDto();
      position.setX((reportIds.indexOf(reportId) % 3) * 6);
      position.setY((reportIds.indexOf(reportId) / 3) * 4);
      report.setPosition(position);

      DimensionDto dimensions = new DimensionDto();
      dimensions.setHeight(4);
      dimensions.setWidth(6);
      report.setDimensions(dimensions);

      return report;
    }).collect(Collectors.toList());

    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setReports(reportLocations);

    return dashboard;
  }
}
