/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.upgrade;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
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
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Generator implements AutoCloseable {

  private final OptimizeClient optimizeClient;
  private String processDefinitionKey;
  private String processDefinitionVersion;

  public Generator() throws IOException {
    optimizeClient = new OptimizeClient();
  }

  public static void main(String[] args) throws Exception {
    try (Generator generator = new Generator()) {
      generator.setDefaultProcessDefinition();

      List<String> reportIds = generator.generateReports();
      generator.generateAlerts();
      generator.generateDashboards(reportIds);

      generator.generateCollection();
    }
  }

  @Override
  public void close() throws Exception {
    this.optimizeClient.close();
  }

  private void setDefaultProcessDefinition() {
    ProcessDefinitionEngineDto processDefinitionEngineDto = getEngineProcessDefinitions().get(0);
    processDefinitionKey = processDefinitionEngineDto.getKey();
    processDefinitionVersion = processDefinitionEngineDto.getVersionAsString();
  }

  private void generateAlerts() {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    reportData.setVisualization(ProcessVisualization.NUMBER);

    List<String> reports = createAndUpdateReports(Collections.singletonList(reportData), new ArrayList<>());

    String reportId = reports.get(0);

    AlertCreationDto alertCreation = prepareAlertCreation(reportId);
    optimizeClient.createAlert(alertCreation);
  }

  private void generateDashboards(List<String> reportIds) {
    DashboardDefinitionDto dashboard = prepareDashboard(reportIds);

    String dashboardId = optimizeClient.createEmptyDashboard();
    optimizeClient.createEmptyDashboard();

    optimizeClient.updateDashboard(dashboardId, dashboard);
  }

  private void generateCollection() {
    final String collectionId = optimizeClient.createCollection();

    final String collectionDashboardId = optimizeClient.createEmptyDashboardInCollection(collectionId);

    final String collectionReport1 = createSingleNumberReportInCollection(collectionId);
    final String collectionReport2 = createSingleNumberReportInCollection(collectionId);

    optimizeClient.addReportsToDashboard(collectionDashboardId, collectionReport1, collectionReport2);
  }

  private String createSingleNumberReportInCollection(final String collectionId) {
    final String collectionReportId = optimizeClient.createEmptySingleProcessReportInCollection(collectionId);
    final ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = prepareReportUpdate(
      reportData, collectionReportId
    );
    optimizeClient.updateReport(collectionReportId, singleProcessReportDefinitionDto);
    return collectionReportId;
  }

  private List<String> generateReports() {
    final List<ProcessReportDataDto> reportDefinitions = createDifferentReports();
    final List<ProcessFilterDto> filters = prepareFilters();

    return createAndUpdateReports(reportDefinitions, filters);
  }

  private List<String> createAndUpdateReports(final List<ProcessReportDataDto> reportDefinitions,
                                              final List<ProcessFilterDto> filters) {
    CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
    combinedReportData.setReports(new ArrayList<>());
    List<String> reportIds = new ArrayList<>();
    for (ProcessReportDataDto reportData : reportDefinitions) {
      String id = optimizeClient.createEmptySingleProcessReport();
      reportIds.add(id);
      // there are two reports expected matching this criteria
      if (reportData.getVisualization().equals(ProcessVisualization.BAR)
        && reportData.getGroupBy().getType().equals(ProcessGroupByType.START_DATE)) {
        combinedReportData.getReports().add(new CombinedReportItemDto(id));
      }
      reportData.setFilter(filters);

      SingleProcessReportDefinitionDto reportUpdate = prepareReportUpdate(reportData, id);
      optimizeClient.updateReport(id, reportUpdate);
    }
    if (!combinedReportData.getReports().isEmpty()) {
      reportIds.add(optimizeClient.createCombinedReport(combinedReportData));
    }
    return reportIds;
  }

  private List<ProcessReportDataDto> createDifferentReports() {
    List<ProcessReportDataDto> reportDefinitions = new ArrayList<>();
    reportDefinitions.add(
      ProcessReportDataBuilder
        .createReportData()
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessDefinitionVersion(processDefinitionVersion)
        .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
        .build()
    );
    reportDefinitions.add(
      ProcessReportDataBuilder
        .createReportData()
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessDefinitionVersion(processDefinitionVersion)
        .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
        .build()
    );
    final ProcessReportDataDto maxFlowNodeDurationGroupByFlowNodeHeatmapReport = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .build();
    maxFlowNodeDurationGroupByFlowNodeHeatmapReport.getConfiguration().setAggregationType(AggregationType.MAX);
    reportDefinitions.add(maxFlowNodeDurationGroupByFlowNodeHeatmapReport);

    ProcessReportDataDto reportDataDto = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    reportDataDto.setVisualization(ProcessVisualization.BAR);
    reportDefinitions.add(reportDataDto);

    reportDataDto = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    reportDataDto.setVisualization(ProcessVisualization.BAR);
    reportDefinitions.add(reportDataDto);

    reportDataDto = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART)
      .setVariableType(VariableType.STRING)
      .setVariableName("var")
      .setStartFlowNodeId("startNode")
      .setEndFlowNodeId("endNode")
      .build();
    reportDataDto.setVisualization(ProcessVisualization.BAR);
    reportDefinitions.add(reportDataDto);

    return reportDefinitions;
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

  private static SingleProcessReportDefinitionDto prepareReportUpdate(ProcessReportDataDto reportData, String id) {
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId(id);
    report.setName(reportData.createCommandKey());
    return report;
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
