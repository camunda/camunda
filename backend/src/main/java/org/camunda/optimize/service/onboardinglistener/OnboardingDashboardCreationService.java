/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.onboardinglistener;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.CountProgressDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.DurationProgressDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.collection.CollectionRoleService;
import org.camunda.optimize.service.collection.CollectionScopeService;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;

@Slf4j
@AllArgsConstructor
@Component
public class OnboardingDashboardCreationService {
  private final DefinitionService definitionService;
  private final ReportService reportService;
  private final DashboardService dashboardService;
  private final CollectionService collectionService;
  private final CollectionScopeService collectionScopeService;
  private CollectionRoleService collectionRoleService;

  private List<ReportLocationDto> generateReportsAutomaticallyForDashboard(final String userId,
                                                                           final String processId,
                                                                           final String collectionId,
                                                                           final List<String> tenants,
                                                                           final ProcessDefinitionOptimizeDto definitionWithXml) {
    List<ReportLocationDto> dashboardReports = new ArrayList<>();
    ReportLocationDto throughput30Day = new ReportLocationDto();
    throughput30Day.setId(create30DayThroughputReport(userId, processId, collectionId, tenants).getId());
    throughput30Day.setPosition(new PositionDto(0, 0));
    throughput30Day.setDimensions(new DimensionDto(3, 2));
    dashboardReports.add(throughput30Day);

    TargetDto durationTargetBelow24Hours = new TargetDto();
    durationTargetBelow24Hours.setIsBelow(true);
    durationTargetBelow24Hours.setValue("24");
    durationTargetBelow24Hours.setUnit(TargetValueUnit.HOURS);
    ReportLocationDto p75Duration = new ReportLocationDto();
    p75Duration.setId(createInstanceDurationReport(userId, processId, collectionId, "75", durationTargetBelow24Hours,
                                                   tenants
                                                   ).getId());
    p75Duration.setPosition(new PositionDto(3, 0));
    p75Duration.setDimensions(new DimensionDto(4, 2));
    dashboardReports.add(p75Duration);

    TargetDto durationTargetBelow7Days = new TargetDto();
    durationTargetBelow7Days.setIsBelow(true);
    durationTargetBelow7Days.setValue("7");
    durationTargetBelow7Days.setUnit(TargetValueUnit.DAYS);
    ReportLocationDto p99Duration = new ReportLocationDto();
    p99Duration.setId(createInstanceDurationReport(userId, processId, collectionId, "99", durationTargetBelow7Days,
                                                   tenants).getId());
    p99Duration.setPosition(new PositionDto(7, 0));
    p99Duration.setDimensions(new DimensionDto(4, 2));
    dashboardReports.add(p99Duration);

    ReportLocationDto percentSLAMet = new ReportLocationDto();
    percentSLAMet.setId(createPercentSLAMetReport(userId, processId, collectionId, tenants).getId());
    percentSLAMet.setPosition(new PositionDto(11, 0));
    percentSLAMet.setDimensions(new DimensionDto(3, 2));
    dashboardReports.add(percentSLAMet);

    ReportLocationDto percentNoIncidents = new ReportLocationDto();
    percentNoIncidents.setId(createPercentIncidentFreeReport(userId, processId, collectionId, tenants).getId());
    percentNoIncidents.setPosition(new PositionDto(14, 0));
    percentNoIncidents.setDimensions(new DimensionDto(4, 2));
    dashboardReports.add(percentNoIncidents);

    ReportLocationDto heatMap1 = new ReportLocationDto();
    heatMap1.setId(
      createDurationHeatmapReport(userId, processId, collectionId,
                                  ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE,
                                  "Which process steps take too much time?", tenants,
                                  definitionWithXml.getBpmn20Xml()).getId());
    heatMap1.setPosition(new PositionDto(0, 2));
    heatMap1.setDimensions(new DimensionDto(9, 5));
    dashboardReports.add(heatMap1);

    ReportLocationDto controlChart = new ReportLocationDto();
    controlChart.setId(createProcessDurationLineChart(userId, processId, collectionId, tenants).getId());
    controlChart.setPosition(new PositionDto(9, 2));
    controlChart.setDimensions(new DimensionDto(9, 5));
    dashboardReports.add(controlChart);

    ReportLocationDto heatMap2 = new ReportLocationDto();
    heatMap2.setId(createFrequencyHeatmapReport(userId, processId, collectionId,
                                                ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE,
                                                "How often is each process step run?", tenants,
                                                definitionWithXml.getBpmn20Xml()).getId());
    heatMap2.setPosition(new PositionDto(0, 7));
    heatMap2.setDimensions(new DimensionDto(9, 5));
    dashboardReports.add(heatMap2);

    ReportLocationDto barReport = new ReportLocationDto();
    barReport.setId(createProcessRunFrequency(userId, processId, collectionId, tenants).getId());
    barReport.setPosition(new PositionDto(9, 7));
    barReport.setDimensions(new DimensionDto(9, 5));
    dashboardReports.add(barReport);

    ReportLocationDto heatMap3 = new ReportLocationDto();
    heatMap3.setId(createFrequencyHeatmapReport(userId, processId, collectionId,
                                                ProcessReportDataType.INCIDENT_FREQ_GROUP_BY_FLOW_NODE,
                                                "Where are the active incidents?",
                                                tenants,
                                                definitionWithXml.getBpmn20Xml()).getId());
    heatMap3.setPosition(new PositionDto(0, 12));
    heatMap3.setDimensions(new DimensionDto(9, 5));
    dashboardReports.add(heatMap3);

    ReportLocationDto heatMap4 = new ReportLocationDto();
    heatMap4.setId(
      createDurationHeatmapReport(userId, processId, collectionId,
                                  ProcessReportDataType.INCIDENT_DUR_GROUP_BY_FLOW_NODE,
                                  "Where are the worst incidents",
                                  tenants,
                                  definitionWithXml.getBpmn20Xml()).getId());
    heatMap4.setPosition(new PositionDto(9, 12));
    heatMap4.setDimensions(new DimensionDto(9, 5));
    dashboardReports.add(heatMap4);

    ReportLocationDto incidentHandlingTrend = new ReportLocationDto();
    incidentHandlingTrend.setId(createIncidentHandlingTrends(userId, processId, collectionId, tenants).getId());
    incidentHandlingTrend.setPosition(new PositionDto(0, 17));
    incidentHandlingTrend.setDimensions(new DimensionDto(18, 5));
    dashboardReports.add(incidentHandlingTrend);

    return dashboardReports;
  }

  private IdResponseDto createIncidentHandlingTrends(final String userId,
                                                     final String processId,
                                                     final String collectionId,
                                                     final List<String> tenants) {


    List<ProcessFilterDto<?>> resolvedIncidents = ProcessFilterBuilder
      .filter()
      .withResolvedIncident()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .add()
      .buildList();
    resolvedIncidents.get(0).setAppliedTo(List.of("all"));

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processId)
      .setProcessDefinitionVersion("all")
      .setTenantIds(tenants)
      .setFilter(resolvedIncidents)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setVisualization(ProcessVisualization.BARLINE)
      .build();

    reportData.getView().setProperties(ViewProperty.FREQUENCY, ViewProperty.DURATION);

    StartDateGroupByDto groupBy = new StartDateGroupByDto();
    final DateGroupByValueDto dateGroupBy = new DateGroupByValueDto();
    dateGroupBy.setUnit(AggregateByDateUnit.WEEK);
    groupBy.setValue(dateGroupBy);
    reportData.setGroupBy(groupBy);

    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName("Are we improving incident handling?");
    report.setCollectionId(collectionId);
    return reportService.createNewSingleProcessReport(userId, report);

  }

  private IdResponseDto createProcessRunFrequency(final String userId,
                                                  final String processId,
                                                  final String collectionId,
                                                  final List<String> tenants) {

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processId)
      .setProcessDefinitionVersion("all")
      .setTenantIds(tenants)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE)
      .setVisualization(ProcessVisualization.BAR)
      .build();

    StartDateGroupByDto groupBy = new StartDateGroupByDto();
    final DateGroupByValueDto dateGroupBy = new DateGroupByValueDto();
    dateGroupBy.setUnit(AggregateByDateUnit.WEEK);
    groupBy.setValue(dateGroupBy);
    reportData.setGroupBy(groupBy);

    reportData.getConfiguration().setXLabel("Start Date");
    reportData.getConfiguration().setYLabel("Process Instance Count");
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName("How frequently is this process run?");
    report.setCollectionId(collectionId);
    return reportService.createNewSingleProcessReport(userId, report);

  }

  private IdResponseDto createProcessDurationLineChart(final String userId,
                                                       final String processId,
                                                       final String collectionId,
                                                       final List<String> tenants) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processId)
      .setProcessDefinitionVersion("all")
      .setTenantIds(tenants)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE)
      .setVisualization(ProcessVisualization.LINE)
      .build();

    StartDateGroupByDto groupBy = new StartDateGroupByDto();
    final DateGroupByValueDto dateGroupBy = new DateGroupByValueDto();
    dateGroupBy.setUnit(AggregateByDateUnit.WEEK);
    groupBy.setValue(dateGroupBy);
    reportData.setGroupBy(groupBy);

    ArrayList<Double> values = new ArrayList<>(Arrays.asList(99.0, 90.0, 75.0, 50.0));
    ArrayList<AggregationDto> aggDtos = new ArrayList<>();
    for (Double value : values) {
      AggregationDto aggDto = new AggregationDto();
      aggDto.setValue(value);
      aggDto.setType(AggregationType.PERCENTILE);
      aggDtos.add(aggDto);
    }
    reportData.getConfiguration().setAggregationTypes(aggDtos.toArray(new AggregationDto[values.size()]));
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName("Is my process within control?");
    report.setCollectionId(collectionId);
    return reportService.createNewSingleProcessReport(userId, report);

  }

  private IdResponseDto createPercentIncidentFreeReport(final String userId,
                                                        final String processId,
                                                        final String collectionId,
                                                        final List<String> tenants) {
    List<ProcessFilterDto<?>> noIncidents = ProcessFilterBuilder
      .filter()
      .noIncidents()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .add()
      .buildList();
    noIncidents.get(0).setAppliedTo(List.of("all"));

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setFilter(noIncidents)
      .setProcessDefinitionKey(processId)
      .setProcessDefinitionVersion("all")
      .setTenantIds(tenants)
      .setReportDataType(ProcessReportDataType.PROC_INST_PER_GROUP_BY_NONE)
      .setVisualization(ProcessVisualization.NUMBER)

      .build();
    SingleReportTargetValueDto targetValue = new SingleReportTargetValueDto();
    targetValue.setIsKpi(true);
    targetValue.setActive(true);
    CountProgressDto countProgressDto = new CountProgressDto();
    countProgressDto.setTarget("99");
    countProgressDto.setBaseline("0");
    targetValue.setCountProgress(countProgressDto);
    reportData.getConfiguration().setTargetValue(targetValue);
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName("Incident-Free Rate");
    report.setCollectionId(collectionId);
    return reportService.createNewSingleProcessReport(userId, report);
  }

  private IdResponseDto createPercentSLAMetReport(final String userId,
                                                  final String processId,
                                                  final String collectionId,
                                                  final List<String> tenants) {
    List<ProcessFilterDto<?>> lessThan7Days = ProcessFilterBuilder
      .filter()
      .duration()
      .unit(DurationUnit.DAYS)
      .value((long) 7)
      .operator(LESS_THAN)
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .add()
      .buildList();
    lessThan7Days.get(0).setAppliedTo(List.of("all"));

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setFilter(lessThan7Days)
      .setProcessDefinitionKey(processId)
      .setProcessDefinitionVersion("all")
      .setTenantIds(tenants)
      .setReportDataType(ProcessReportDataType.PROC_INST_PER_GROUP_BY_NONE)
      .setVisualization(ProcessVisualization.NUMBER)

      .build();
    SingleReportTargetValueDto targetValue = new SingleReportTargetValueDto();
    targetValue.setIsKpi(true);
    targetValue.setActive(true);
    CountProgressDto countProgressDto = new CountProgressDto();
    countProgressDto.setTarget("99");
    countProgressDto.setBaseline("0");
    targetValue.setCountProgress(countProgressDto);
    reportData.getConfiguration().setTargetValue(targetValue);
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName("% SLA Met");
    report.setCollectionId(collectionId);
    return reportService.createNewSingleProcessReport(userId, report);
  }

  private IdResponseDto createInstanceDurationReport(final String userId,
                                                     final String processId,
                                                     final String collectionId,
                                                     final String percentile,
                                                     final TargetDto durationTarget,
                                                     final List<String> tenants) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processId)
      .setProcessDefinitionVersion("all")
      .setTenantIds(tenants)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .setVisualization(ProcessVisualization.NUMBER)
      .build();
    SingleReportTargetValueDto targetValue = new SingleReportTargetValueDto();
    targetValue.setIsKpi(true);
    targetValue.setActive(true);
    DurationProgressDto durationProgressDto = new DurationProgressDto();
    durationProgressDto.setTarget(durationTarget);
    targetValue.setDurationProgress(durationProgressDto);
    reportData.getConfiguration().setTargetValue(targetValue);
    reportData.getConfiguration().setPrecision(1);
    AggregationDto aggDto = new AggregationDto();
    aggDto.setValue(Double.parseDouble(percentile));
    aggDto.setType(AggregationType.PERCENTILE);
    reportData.getConfiguration().setAggregationTypes(aggDto);
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName("P" + percentile + " Duration");
    report.setCollectionId(collectionId);
    return reportService.createNewSingleProcessReport(userId, report);
  }

  private IdResponseDto createDurationHeatmapReport(final String userId,
                                                    final String processId,
                                                    final String collectionId,
                                                    final ProcessReportDataType reportType,
                                                    final String title,
                                                    final List<String> tenants,
                                                    final String xml) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processId)
      .setProcessDefinitionVersion("all")
      .setTenantIds(tenants)
      .setReportDataType(reportType)
      .setVisualization(ProcessVisualization.HEAT)
      .build();

    AggregationDto aggDtoPerc = new AggregationDto();
    aggDtoPerc.setValue(50.0);
    aggDtoPerc.setType(AggregationType.PERCENTILE);
    AggregationDto aggDtoMax = new AggregationDto();
    aggDtoMax.setType(AggregationType.MAX);
    AggregationDto aggDtoAvg = new AggregationDto();
    aggDtoAvg.setType(AggregationType.AVERAGE);

    reportData.getConfiguration().setAggregationTypes(aggDtoAvg, aggDtoPerc, aggDtoMax);
    reportData.getConfiguration().setXml(xml);
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName(title);
    report.setCollectionId(collectionId);
    return reportService.createNewSingleProcessReport(userId, report);
  }

  private IdResponseDto createFrequencyHeatmapReport(final String userId,
                                                     final String processId,
                                                     final String collectionId,
                                                     final ProcessReportDataType reportType,
                                                     final String title,
                                                     final List<String> tenants,
                                                     final String xml) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processId)
      .setProcessDefinitionVersion("all")
      .setTenantIds(tenants)
      .setReportDataType(reportType)
      .setVisualization(ProcessVisualization.HEAT)
      .build();

    reportData.getConfiguration().setXml(xml);
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName(title);
    report.setCollectionId(collectionId);
    return reportService.createNewSingleProcessReport(userId, report);
  }

  private IdResponseDto create30DayThroughputReport(final String userId, final String processId,
                                                    final String collectionId, final List<String> tenants) {
    final RollingDateFilterDataDto filterData = new RollingDateFilterDataDto(new RollingDateFilterStartDto(
      30L, DateUnit.DAYS));
    final InstanceEndDateFilterDto endDateFilter = new InstanceEndDateFilterDto();
    endDateFilter.setData(filterData);
    endDateFilter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    endDateFilter.setAppliedTo(List.of(ReportConstants.APPLIED_TO_ALL_DEFINITIONS));
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processId)
      .setProcessDefinitionVersion("all")
      .setTenantIds(tenants)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .setVisualization(ProcessVisualization.NUMBER)
      .setFilter(endDateFilter)
      .build();
    SingleReportTargetValueDto targetValue = new SingleReportTargetValueDto();
    targetValue.setIsKpi(true);
    targetValue.setActive(true);
    CountProgressDto countProgressDto = new CountProgressDto();
    countProgressDto.setTarget("200");
    targetValue.setCountProgress(countProgressDto);
    reportData.getConfiguration().setTargetValue(targetValue);
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName("Throughput (30-day rolling)");
    report.setCollectionId(collectionId);
    return reportService.createNewSingleProcessReport(userId, report);
  }

  private Optional<String> createCollectionForDashboard(final String userId,
                                                        final String processId,
                                                        final List<String> tenants,
                                                        final String collectionName) {
    final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto =
      new PartialCollectionDefinitionRequestDto();
    partialCollectionDefinitionDto.setName(collectionName);
    final Optional<IdResponseDto> response = collectionService.createNewCollectionWithPresetId (
      userId, partialCollectionDefinitionDto, processId,true);
    return response.map(res -> {
        CollectionScopeEntryDto scopeUpdate = new CollectionScopeEntryDto(DefinitionType.PROCESS, processId, tenants);
        collectionScopeService.addScopeEntriesToCollection(userId, res.getId(), List.of(scopeUpdate));
        return res.getId();
      });
  }

  public void addUserAsEditorToAutomaticallyCreatedCollection(final String collectionId, final String userId) {
    IdentityDto user = new IdentityDto(userId, IdentityType.USER);
    collectionRoleService.addUserAsEditorToAutomaticallyCreatedCollection(collectionId, user);
  }

  public IdResponseDto createNewDashboardForProcess(final String userId, final String processId) {
    DefinitionResponseDto definition = definitionService.getDefinitionWithAvailableTenants(
        DefinitionType.PROCESS,
        processId,
        userId
      )
      .orElseThrow(() -> {
        final String reason =
          String.format("Did not automatically create dashboard for process [%s] because it doesn't " +
                          "exist.", processId);
        log.error(reason);
        return new NotFoundException(reason);
      });
    List<String> tenants = definition.getTenants().stream().map(TenantDto::getId).collect(
      Collectors.toList());

    Optional<String> createdCollectionId = createCollectionForDashboard(userId, processId, tenants, definition.getName());
    if (createdCollectionId.isPresent()) {
      final Optional<ProcessDefinitionOptimizeDto> definitionWithXml =
        definitionService.getDefinitionWithXml(DefinitionType.PROCESS, userId, processId, "all", tenants.get(0));

      return definitionWithXml.map(def -> {
        List<ReportLocationDto> dashboardReports =
          generateReportsAutomaticallyForDashboard(userId, processId, createdCollectionId.get(), tenants, def);

        DashboardDefinitionRestDto dashboardDef = new DashboardDefinitionRestDto();
        dashboardDef.setReports(dashboardReports);
        dashboardDef.setCollectionId(createdCollectionId.get());
        dashboardDef.setName(definition.getName());
        collectionService.updatePartialCollection(userId, createdCollectionId.get(), new PartialCollectionDefinitionRequestDto());
        return dashboardService.createNewDashboardWithPresetId(userId, dashboardDef, processId).get();
      }).orElseThrow(() -> {
        final String reason =
          String.format("Did not automatically create dashboard for process [%s] because it doesn't " +
                          "exist.", processId);
        log.error(reason);
        return new NotFoundException(reason);
      });
    } else {
      IdResponseDto id = new IdResponseDto(processId);
      if (dashboardService.getAllDashboardIdsInCollection(processId).contains(id)) {
        return id;
      } else {
        return null;
      }
    }
  }
}
