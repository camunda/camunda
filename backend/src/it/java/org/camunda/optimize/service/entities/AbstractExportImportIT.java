/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DashboardFilterType;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.CombinedReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value.CombinedReportCountChartDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value.CombinedReportDurationChartDto;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value.CombinedReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByInputVariableDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.BeforeEach;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewProperty.FREQUENCY;
import static org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewProperty.RAW_DATA;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRollingEvaluationDateFilter;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public abstract class AbstractExportImportIT extends AbstractIT {
  protected static final String DEFINITION_KEY = "aKey";
  protected static final String DEFINITION_NAME = "aDefinitionName";
  protected static final String DEFINITION_VERSION = "1";
  protected static final String DEFINITION_XML_STRING = "xmlString";
  protected static final String VALID_DECISION_REPORT_ID = "11111111-0000-0000-0000-000000000000";
  protected static final String VALID_PROCESS_REPORT_ID = "22222222-0000-0000-0000-000000000000";
  protected static final String VALID_COMBINED_REPORT_ID = "33333333-0000-0000-0000-000000000000";

  @BeforeEach
  public void setUp() {
    // only superusers are authorized to export reports
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(DEFAULT_USERNAME);
  }

  @SuppressWarnings(UNUSED)
  protected static Stream<ReportType> reportTypes() {
    return Stream.of(ReportType.PROCESS, ReportType.DECISION);
  }

  @SuppressWarnings(UNUSED)
  protected static Stream<SingleDecisionReportDefinitionRequestDto> getTestDecisionReports() {
    // A raw data report with custom table configs
    final DecisionReportDataDto rawReport = new DecisionReportDataDto();
    rawReport.setDecisionDefinitionKey(DEFINITION_KEY);
    rawReport.setDecisionDefinitionVersion(DEFINITION_VERSION);
    rawReport.setVisualization(DecisionVisualization.TABLE);
    final DecisionViewDto rawDataView = new DecisionViewDto();
    rawDataView.setProperty(RAW_DATA);
    rawReport.setView(rawDataView);
    rawReport.getConfiguration().getTableColumns().setIncludeNewVariables(false);
    rawReport.getConfiguration().getTableColumns().getExcludedColumns().add(DecisionInstanceDto.Fields.engine);

    // A groupBy variable report with filters and custom bucket config
    final DecisionReportDataDto groupByVarReport = new DecisionReportDataDto();
    groupByVarReport.setDecisionDefinitionKey(DEFINITION_KEY);
    groupByVarReport.setDecisionDefinitionVersion(DEFINITION_VERSION);
    final DecisionViewDto evalCountView = new DecisionViewDto();
    evalCountView.setProperty(FREQUENCY);
    groupByVarReport.setView(evalCountView);
    groupByVarReport.setVisualization(DecisionVisualization.BAR);
    final DecisionGroupByVariableValueDto variableValueDto = new DecisionGroupByVariableValueDto();
    variableValueDto.setId("testVariableID");
    variableValueDto.setName("testVariableName");
    variableValueDto.setType(VariableType.INTEGER);
    final DecisionGroupByInputVariableDto groupByDto = new DecisionGroupByInputVariableDto();
    groupByDto.setValue(variableValueDto);
    groupByVarReport.setGroupBy(new DecisionGroupByInputVariableDto());
    groupByVarReport.getFilter().add(createRollingEvaluationDateFilter(1L, DateFilterUnit.DAYS));
    groupByVarReport.getConfiguration().getCustomBucket().setActive(true);
    groupByVarReport.getConfiguration().getCustomBucket().setBaseline(500.0);
    groupByVarReport.getConfiguration().getCustomBucket().setBucketSize(15.0);

    return Stream.of(
      createDecisionReportDefinition(rawReport),
      createDecisionReportDefinition(groupByVarReport)
    );
  }

  @SuppressWarnings(UNUSED)
  protected static Stream<SingleProcessReportDefinitionRequestDto> getTestProcessReports() {
    // A raw report with some custom table column config
    final ProcessReportDataDto rawReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    rawReport.getConfiguration().getTableColumns().setIncludeNewVariables(false);
    rawReport.getConfiguration().getTableColumns().getExcludedColumns().add(ProcessInstanceDto.Fields.startDate);

    // A groupBy report with process part and custom sorting
    final ProcessReportDataDto durationWithPartReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setGroupByDateVariableUnit(AggregateByDateUnit.HOUR)
      .setStartFlowNodeId("someStartFlowNode")
      .setEndFlowNodeId("someEndFlowNode")
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_END_DATE_WITH_PART)
      .build();
    durationWithPartReport.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));

    // A distributedBy report with filters and custom bucket config
    final RollingDateFilterDataDto filterData = new RollingDateFilterDataDto(new RollingDateFilterStartDto(
      4L, DateFilterUnit.DAYS));
    final EndDateFilterDto endDateFilter = new EndDateFilterDto();
    endDateFilter.setData(filterData);
    endDateFilter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    final ProcessReportDataDto filteredDistrByReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setDistributeByDateInterval(AggregateByDateUnit.YEAR)
      .setGroupByDateVariableUnit(AggregateByDateUnit.DAY)
      .setVariableType(VariableType.INTEGER)
      .setVariableName("testVariable")
      .setFilter(endDateFilter)
      .setVisualization(ProcessVisualization.BAR)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE_BY_START_DATE)
      .build();
    filteredDistrByReport.getConfiguration().getCustomBucket().setBucketSize(150.0);
    filteredDistrByReport.getConfiguration().getCustomBucket().setBaseline(55.0);
    filteredDistrByReport.getConfiguration().getCustomBucket().setActive(true);

    return Stream.of(
      createProcessReportDefinition(rawReport),
      createProcessReportDefinition(durationWithPartReport),
      createProcessReportDefinition(filteredDistrByReport)
    );
  }

  @SuppressWarnings(UNUSED)
  protected static Stream<List<SingleProcessReportDefinitionRequestDto>> getTestCombinableReports() {
    // a groupBy startDate report
    final ProcessReportDataDto byStartDateData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .build();
    byStartDateData.setVisualization(ProcessVisualization.BAR);
    final SingleProcessReportDefinitionRequestDto startDateReport = createProcessReportDefinition(byStartDateData);
    startDateReport.setName("startDateReportName");
    startDateReport.setId("startDateReportId");

    // a groupBy endDate report
    final ProcessReportDataDto byEndDateData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE)
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .build();
    byEndDateData.setVisualization(ProcessVisualization.BAR);
    final SingleProcessReportDefinitionRequestDto endDateReport = createProcessReportDefinition(byEndDateData);
    endDateReport.setName("endDateReportName");
    endDateReport.setId("endDateReportId");

    // a userTask duration report
    final ProcessReportDataDto userTaskDurData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_USER_TASK)
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .build();
    userTaskDurData.setVisualization(ProcessVisualization.BAR);
    final SingleProcessReportDefinitionRequestDto userTaskReport = createProcessReportDefinition(userTaskDurData);
    userTaskReport.setName("userTaskReportName");
    userTaskReport.setId("userTaskReportId");

    // a flownode duration report
    final ProcessReportDataDto flowNodeDurData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE)
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setVisualization(ProcessVisualization.BAR)
      .build();
    flowNodeDurData.setVisualization(ProcessVisualization.BAR);
    final SingleProcessReportDefinitionRequestDto flowNodeReport = createProcessReportDefinition(flowNodeDurData);
    flowNodeReport.setName("flowNodeReportName");
    flowNodeReport.setId("flowNodeReportId");

    return Stream.of(
      Arrays.asList(startDateReport, endDateReport),
      Arrays.asList(userTaskReport, flowNodeReport)
    );
  }

  protected String createSimpleReport(final ReportType reportType) {
    switch (reportType) {
      case PROCESS:
        final ProcessReportDataDto processReportData = createSimpleProcessReportData();
        return reportClient.createSingleProcessReport(processReportData);
      case DECISION:
        final DecisionReportDataDto decisionReportData = createSimpleDecisionReportData();
        return reportClient.createSingleDecisionReport(decisionReportData);
      default:
        throw new OptimizeIntegrationTestException("Unknown report type: " + reportType);
    }
  }

  protected void createAndSaveDefinition(final DefinitionType definitionType,
                                         final String tenantId) {
    createAndSaveDefinition(definitionType, tenantId, DEFINITION_VERSION);
  }

  protected void createAndSaveDefinition(final DefinitionType definitionType,
                                         final String tenantId,
                                         final String version) {
    switch (definitionType) {
      case PROCESS:
        final ProcessDefinitionOptimizeDto processDefinition = createProcessDefinition(tenantId, version);
        elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
          PROCESS_DEFINITION_INDEX_NAME,
          processDefinition.getId(),
          processDefinition
        );
        break;
      case DECISION:
        final DecisionDefinitionOptimizeDto decisionDefinition = createDecisionDefinition(tenantId, version);
        elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
          DECISION_DEFINITION_INDEX_NAME,
          decisionDefinition.getId(),
          decisionDefinition
        );
        break;
      default:
        throw new OptimizeIntegrationTestException("Unknown definition type: " + definitionType);
    }
  }

  protected static SingleProcessReportDefinitionRequestDto createSimpleProcessReportDefinition() {
    return createProcessReportDefinition(createSimpleProcessReportData());
  }

  protected static SingleDecisionReportDefinitionRequestDto createSimpleDecisionReportDefinition() {
    return createDecisionReportDefinition(createSimpleDecisionReportData());
  }

  protected static CombinedReportDefinitionRequestDto createCombinedReportDefinition(
    final List<SingleProcessReportDefinitionRequestDto> singleReports) {
    final CombinedReportConfigurationDto combinedConfig = new CombinedReportConfigurationDto();
    combinedConfig.setPointMarkers(false);
    combinedConfig.setXLabel("some x label");
    combinedConfig.setYLabel("some y label");
    combinedConfig.setTargetValue(new CombinedReportTargetValueDto(
      new CombinedReportCountChartDto(true, "500"),
      true,
      new CombinedReportDurationChartDto(TargetValueUnit.MINUTES, true, "5")
    ));

    final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
    combinedReportData.setReports(
      singleReports.stream()
        .map(report -> new CombinedReportItemDto(report.getId(), "#1991c8"))
        .collect(toList())
    );
    combinedReportData.setVisualization(singleReports.get(0).getData().getVisualization());
    combinedReportData.setConfiguration(combinedConfig);

    final CombinedReportDefinitionRequestDto combinedReportDef = new CombinedReportDefinitionRequestDto();
    combinedReportDef.setData(combinedReportData);
    combinedReportDef.setId("combinedReportId");
    combinedReportDef.setName("combinedReportName");
    combinedReportDef.setCreated(OffsetDateTime.parse("2019-01-01T00:00:00+00:00"));
    combinedReportDef.setLastModified(OffsetDateTime.parse("2019-01-02T00:00:00+00:00"));
    combinedReportDef.setLastModifier("lastModifierId");
    combinedReportDef.setOwner("ownerId");

    return combinedReportDef;
  }

  protected static DashboardDefinitionRestDto createDashboardDefinition(
    final List<String> reportIds) {
    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setName("A Dashboard Name");
    dashboard.setId("dashboardId");
    dashboard.setReports(
      reportIds.stream()
        .map(reportId -> ReportLocationDto.builder()
          .id(reportId)
          .dimensions(new DimensionDto(5, 15))
          .position(new PositionDto(20, 25))
          .build())
        .collect(toList())
    );
    dashboard.setLastModifier("lastModifier");
    dashboard.setOwner("owner");
    dashboard.setCreated(OffsetDateTime.parse("2019-01-01T00:00:00+00:00"));
    dashboard.setLastModified(OffsetDateTime.parse("2019-01-01T00:00:00+00:00"));
    dashboard.setAvailableFilters(Arrays.asList(
      new DashboardFilterDto(DashboardFilterType.START_DATE, null),
      new DashboardFilterDto(DashboardFilterType.END_DATE, null)
    ));

    return dashboard;
  }

  protected static ReportDefinitionExportDto createSimpleExportDto(final ReportType type) {
    return createSimpleExportDtoWithTenants(type, Collections.singletonList(null));
  }

  protected static ReportDefinitionExportDto createSimpleExportDtoWithTenants(final ReportType type,
                                                                              final List<String> tenantIds) {
    switch (type) {
      case PROCESS:
        final SingleProcessReportDefinitionExportDto processDef = createSimpleProcessExportDto();
        processDef.getData().setTenantIds(tenantIds);
        return processDef;
      case DECISION:
        final SingleDecisionReportDefinitionExportDto decisionDef = createSimpleDecisionExportDto();
        decisionDef.getData().setTenantIds(tenantIds);
        return decisionDef;
      default:
        throw new OptimizeIntegrationTestException("Unknown report type: " + type);
    }
  }

  protected static SingleProcessReportDefinitionExportDto createSimpleProcessExportDto() {
    return new SingleProcessReportDefinitionExportDto(createSimpleProcessReportDefinition());
  }

  protected static SingleDecisionReportDefinitionExportDto createSimpleDecisionExportDto() {
    return new SingleDecisionReportDefinitionExportDto(createSimpleDecisionReportDefinition());
  }

  protected static CombinedProcessReportDefinitionExportDto createSimpleCombinedExportDto() {
    final SingleProcessReportDefinitionExportDto report = createSimpleProcessExportDto();
    final CombinedReportDataDto combinedData = new CombinedReportDataDto();
    combinedData.setReports(Collections.singletonList(new CombinedReportItemDto(report.getId())));
    final CombinedReportDefinitionRequestDto combinedReportDef = new CombinedReportDefinitionRequestDto();
    combinedReportDef.setId(VALID_COMBINED_REPORT_ID);
    combinedReportDef.setData(combinedData);
    combinedReportDef.setName("A combined report");
    return new CombinedProcessReportDefinitionExportDto(combinedReportDef);
  }

  protected static DashboardDefinitionExportDto createSimpleDashboardExportDto() {
    final DashboardDefinitionRestDto dashboardDef = new DashboardDefinitionRestDto();
    dashboardDef.setName("Test Dashboard");
    dashboardDef.setId("dashboardId");

    return new DashboardDefinitionExportDto(dashboardDef);
  }

  protected static SingleProcessReportDefinitionExportDto createExportDto(
    final SingleProcessReportDefinitionRequestDto reportDefToImport) {
    return new SingleProcessReportDefinitionExportDto(reportDefToImport);
  }

  protected static SingleDecisionReportDefinitionExportDto createExportDto(
    final SingleDecisionReportDefinitionRequestDto reportDefToImport) {
    return new SingleDecisionReportDefinitionExportDto(reportDefToImport);
  }

  protected static CombinedProcessReportDefinitionExportDto createExportDto(
    final CombinedReportDefinitionRequestDto reportDefToImport) {
    return new CombinedProcessReportDefinitionExportDto(reportDefToImport);
  }

  protected static DashboardDefinitionExportDto createExportDto(
    final DashboardDefinitionRestDto reportDefToImport) {
    return new DashboardDefinitionExportDto(reportDefToImport);
  }

  protected static DashboardDefinitionExportDto createDashboardExportDtoWithResources(
    final List<String> resourceIds) {
    final DashboardDefinitionRestDto dashboard = createDashboardDefinition(resourceIds);
    return new DashboardDefinitionExportDto(dashboard);
  }

  private static ProcessDefinitionOptimizeDto createProcessDefinition(final String tenantId, final String version) {
    return ProcessDefinitionOptimizeDto.builder()
      .id(IdGenerator.getNextId())
      .key(DEFINITION_KEY)
      .name(DEFINITION_NAME)
      .version(version)
      .versionTag(version)
      .tenantId(tenantId)
      .engine(DEFAULT_ENGINE_ALIAS)
      .bpmn20Xml(DEFINITION_XML_STRING + version)
      .build();
  }

  private static DecisionDefinitionOptimizeDto createDecisionDefinition(final String tenantId, final String version) {
    return DecisionDefinitionOptimizeDto.builder()
      .id(IdGenerator.getNextId())
      .key(DEFINITION_KEY)
      .name(DEFINITION_NAME)
      .version(version)
      .versionTag(version)
      .tenantId(tenantId)
      .engine(DEFAULT_ENGINE_ALIAS)
      .dmn10Xml(DEFINITION_XML_STRING + version)
      .build();
  }

  private static ProcessReportDataDto createSimpleProcessReportData() {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFINITION_KEY)
      .setProcessDefinitionVersion(DEFINITION_VERSION)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
  }

  private static DecisionReportDataDto createSimpleDecisionReportData() {
    final DecisionReportDataDto decisionReportData = new DecisionReportDataDto();
    decisionReportData.setDecisionDefinitionKey(DEFINITION_KEY);
    decisionReportData.setDecisionDefinitionVersion(DEFINITION_VERSION);
    return decisionReportData;
  }

  private static SingleProcessReportDefinitionRequestDto createProcessReportDefinition(
    final ProcessReportDataDto reportData) {
    final SingleProcessReportDefinitionRequestDto reportDef = new SingleProcessReportDefinitionRequestDto();
    reportDef.setId(VALID_PROCESS_REPORT_ID);
    reportDef.setName("Test Process Report");
    reportDef.setData(reportData);
    reportDef.setCreated(OffsetDateTime.parse("2019-01-01T00:00:00+00:00"));
    reportDef.setLastModified(OffsetDateTime.parse("2019-01-02T00:00:00+00:00"));
    reportDef.setLastModifier("lastModifierId");
    reportDef.setOwner("ownerId");
    return reportDef;
  }

  private static SingleDecisionReportDefinitionRequestDto createDecisionReportDefinition(
    final DecisionReportDataDto reportData) {
    final SingleDecisionReportDefinitionRequestDto reportDef = new SingleDecisionReportDefinitionRequestDto();
    reportDef.setId(VALID_DECISION_REPORT_ID);
    reportDef.setName("Test Decision Report");
    reportDef.setData(reportData);
    reportDef.setCreated(OffsetDateTime.parse("2019-01-01T00:00:00+00:00"));
    reportDef.setLastModified(OffsetDateTime.parse("2019-01-02T00:00:00+00:00"));
    reportDef.setLastModifier("lastModifierId");
    reportDef.setOwner("ownerId");
    return reportDef;
  }
}
