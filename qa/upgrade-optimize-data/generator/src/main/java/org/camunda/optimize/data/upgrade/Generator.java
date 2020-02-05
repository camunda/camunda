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
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.test.optimize.AlertClient;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.camunda.optimize.test.optimize.DashboardClient;
import org.camunda.optimize.test.optimize.ReportClient;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
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

    final ProcessReportDataDto processInstanceDurationReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .setFilter(prepareFilters())
      .build();
    reportIds.add(createProcessReport(processInstanceDurationReport));

    final ProcessReportDataDto flowNodeDurationReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .setFilter(prepareFilters())
      .build();
    reportIds.add(createProcessReport(flowNodeDurationReport));

    final ProcessReportDataDto maxFlowNodeDurationGroupByFlowNodeHeatmapReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .setFilter(prepareFilters())
      .build();
    maxFlowNodeDurationGroupByFlowNodeHeatmapReport.getConfiguration().setAggregationType(AggregationType.MAX);
    reportIds.add(createProcessReport(maxFlowNodeDurationGroupByFlowNodeHeatmapReport));

    final ProcessReportDataDto processInstanceDurationByStartDateBarChart = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE)
      .setDateInterval(GroupByDateUnit.DAY)
      .setFilter(prepareFilters())
      .build();
    processInstanceDurationByStartDateBarChart.setVisualization(ProcessVisualization.BAR);
    // here we want two of the same type to be combined in a combined report to follow
    final String durationByStartDateReportId1 = createProcessReport(maxFlowNodeDurationGroupByFlowNodeHeatmapReport);
    reportIds.add(durationByStartDateReportId1);
    final String durationByStartDateReportId2 = createProcessReport(maxFlowNodeDurationGroupByFlowNodeHeatmapReport);
    reportIds.add(durationByStartDateReportId2);

    reportIds.add(
      reportClient.createCombinedReport(
        null, Lists.newArrayList(durationByStartDateReportId1, durationByStartDateReportId2)
      )
    );

    final ProcessReportDataDto processInstanceDurationGroupByVariableProcessPartBarChart = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setVariableType(VariableType.STRING)
      .setVariableName("var")
      .setStartFlowNodeId("startNode")
      .setEndFlowNodeId("endNode")
      .setFilter(prepareFilters())
      .build();
    processInstanceDurationGroupByVariableProcessPartBarChart.setVisualization(ProcessVisualization.BAR);
    reportIds.add(createProcessReport(processInstanceDurationGroupByVariableProcessPartBarChart));

    return reportIds;
  }

  private String createProcessReport(final ProcessReportDataDto reportData) {
    return reportClient.createSingleProcessReport(new SingleProcessReportDefinitionDto(reportData));
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

  private static List<ProcessFilterDto> prepareFilters() {
    List<ProcessFilterDto> filters = new ArrayList<>();

    StartDateFilterDto dateFilter = prepareStartDateFilter();
    VariableFilterDto variableFilter = prepareBooleanVariableFilter();
    ExecutedFlowNodeFilterDto executedFlowNodeFilter = prepareFlowNodeFilter();

    filters.add(dateFilter);
    filters.add(variableFilter);
    filters.add(executedFlowNodeFilter);
    return filters;
  }

  private static StartDateFilterDto prepareStartDateFilter() {

    FixedDateFilterDataDto dateFilterData = new FixedDateFilterDataDto();
    dateFilterData.setStart(OffsetDateTime.now());
    dateFilterData.setEnd(OffsetDateTime.now().plusDays(1L));
    return new StartDateFilterDto(dateFilterData);
  }

  private static ExecutedFlowNodeFilterDto prepareFlowNodeFilter() {
    ExecutedFlowNodeFilterDto executedFlowNodeFilter = new ExecutedFlowNodeFilterDto();
    ExecutedFlowNodeFilterDataDto executedFlowNodeFilterData = new ExecutedFlowNodeFilterDataDto();

    executedFlowNodeFilterData.setOperator("in");

    List<String> values = new ArrayList<>();
    values.add("flowNode1");
    values.add("flowNode2");
    executedFlowNodeFilterData.setValues(values);

    executedFlowNodeFilter.setData(executedFlowNodeFilterData);
    return executedFlowNodeFilter;
  }

  private static VariableFilterDto prepareBooleanVariableFilter() {
    VariableFilterDto variableFilter = new VariableFilterDto();

    BooleanVariableFilterDataDto booleanVariableFilterDataDto = new BooleanVariableFilterDataDto("true");
    booleanVariableFilterDataDto.setName("var");

    variableFilter.setData(booleanVariableFilterDataDto);

    return variableFilter;
  }
}
