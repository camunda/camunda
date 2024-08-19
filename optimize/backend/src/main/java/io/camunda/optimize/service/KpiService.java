/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import io.camunda.optimize.dto.optimize.query.processoverview.KpiResultDto;
import io.camunda.optimize.dto.optimize.query.processoverview.KpiType;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.DeletedIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutingFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.MultipleVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.NoIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.NonCanceledInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.NonSuspendedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.SuspendedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import io.camunda.optimize.rest.mapper.ReportRestMapper;
import io.camunda.optimize.service.db.es.report.PlainReportEvaluationHandler;
import io.camunda.optimize.service.db.es.report.ReportEvaluationInfo;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.ValidationHelper;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class KpiService {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(KpiService.class);
  private final ReportService reportService;
  private final LocalizationService localizationService;
  private final PlainReportEvaluationHandler reportEvaluationHandler;

  public KpiService(
      final ReportService reportService,
      final LocalizationService localizationService,
      final PlainReportEvaluationHandler reportEvaluationHandler) {
    this.reportService = reportService;
    this.localizationService = localizationService;
    this.reportEvaluationHandler = reportEvaluationHandler;
  }

  public List<KpiResultDto> evaluateKpiReports(final String processDefinitionKey) {
    final List<SingleProcessReportDefinitionRequestDto> kpiReports =
        getValidKpiReportsForProcessDefinition(processDefinitionKey);
    final List<KpiResultDto> kpiResponseDtos = new ArrayList<>();
    for (final SingleProcessReportDefinitionRequestDto report : kpiReports) {
      final SingleReportEvaluationResult<?> evaluationResult =
          (SingleReportEvaluationResult<?>)
              reportEvaluationHandler
                  .evaluateReport(
                      ReportEvaluationInfo.builder(report).timezone(ZoneId.systemDefault()).build())
                  .getEvaluationResult();
      if (evaluationResult.getFirstCommandResult().getFirstMeasureData() instanceof Double
          || evaluationResult.getFirstCommandResult().getFirstMeasureData() == null) {
        final Double evaluationValue =
            (Double) evaluationResult.getFirstCommandResult().getFirstMeasureData();
        final KpiResultDto kpiResponseDto = new KpiResultDto();
        kpiResponseDto.setReportId(report.getId());
        kpiResponseDto.setCollectionId(report.getCollectionId());
        if (evaluationValue != null) {
          kpiResponseDto.setValue(evaluationValue.toString());
        }
        kpiResponseDtos.add(kpiResponseDto);
      }
    }
    return kpiResponseDtos;
  }

  public List<KpiResultDto> extractMostRecentKpiResultsForCurrentKpiReportsForProcess(
      final ProcessOverviewDto processOverviewDto, final String locale) {
    final List<KpiResultDto> kpiResponseDtos = new ArrayList<>();
    final List<SingleProcessReportDefinitionRequestDto> currentKpiReports =
        getValidKpiReportsForProcessDefinition(processOverviewDto.getProcessDefinitionKey());
    final Map<String, String> lastKpiEvaluationResults =
        Optional.ofNullable(processOverviewDto.getLastKpiEvaluationResults())
            .orElse(Collections.emptyMap());
    for (final SingleProcessReportDefinitionRequestDto report : currentKpiReports) {
      // If the most recent results don't include one of the current KPI reports, we exclude it from
      // the results
      if (lastKpiEvaluationResults.containsKey(report.getId())) {
        ReportRestMapper.localizeReportData(report, locale, localizationService);
        final KpiResultDto kpiResponseDto = new KpiResultDto();
        kpiResponseDto.setValue(lastKpiEvaluationResults.get(report.getId()));
        getTargetAndUnit(report)
            .ifPresent(
                targetAndUnit -> {
                  kpiResponseDto.setTarget(targetAndUnit.getTarget());
                  kpiResponseDto.setUnit(targetAndUnit.getTargetValueUnit());
                });
        kpiResponseDto.setReportId(report.getId());
        kpiResponseDto.setCollectionId(report.getCollectionId());
        kpiResponseDto.setReportName(report.getName());
        kpiResponseDto.setBelow(getIsBelow(report));
        kpiResponseDto.setType(getKpiType(report));
        kpiResponseDto.setMeasure(getViewProperty(report).orElse(null));
        kpiResponseDtos.add(kpiResponseDto);
      }
    }
    return kpiResponseDtos;
  }

  private KpiType getKpiType(
      final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    return getViewProperty(singleProcessReportDefinitionRequestDto)
        .filter(
            measure ->
                ViewProperty.DURATION.equals(measure)
                    || ViewProperty.PERCENTAGE.equals(measure)
                        && !containsQualityFilter(singleProcessReportDefinitionRequestDto))
        .map(measure -> KpiType.TIME)
        .orElse(KpiType.QUALITY);
  }

  private boolean containsQualityFilter(
      final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    return singleProcessReportDefinitionRequestDto.getData().getFilter().stream()
        .anyMatch(
            processFilter ->
                processFilter instanceof FlowNodeStartDateFilterDto
                    || processFilter instanceof FlowNodeEndDateFilterDto
                    || processFilter instanceof VariableFilterDto
                    || processFilter instanceof MultipleVariableFilterDto
                    || processFilter instanceof ExecutedFlowNodeFilterDto
                    || processFilter instanceof ExecutingFlowNodeFilterDto
                    || processFilter instanceof CanceledFlowNodeFilterDto
                    || processFilter instanceof RunningInstancesOnlyFilterDto
                    || processFilter instanceof CompletedInstancesOnlyFilterDto
                    || processFilter instanceof CanceledInstancesOnlyFilterDto
                    || processFilter instanceof NonCanceledInstancesOnlyFilterDto
                    || processFilter instanceof SuspendedInstancesOnlyFilterDto
                    || processFilter instanceof NonSuspendedInstancesOnlyFilterDto
                    || processFilter instanceof FlowNodeDurationFilterDto
                    || processFilter instanceof OpenIncidentFilterDto
                    || processFilter instanceof DeletedIncidentFilterDto
                    || processFilter instanceof ResolvedIncidentFilterDto
                    || processFilter instanceof NoIncidentFilterDto
                    || processFilter instanceof RunningFlowNodesOnlyFilterDto
                    || processFilter instanceof CompletedFlowNodesOnlyFilterDto
                    || processFilter instanceof CanceledFlowNodesOnlyFilterDto
                    || processFilter instanceof CompletedOrCanceledFlowNodesOnlyFilterDto);
  }

  private boolean getIsBelow(
      final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    final SingleReportTargetValueDto targetValue =
        singleProcessReportDefinitionRequestDto.getData().getConfiguration().getTargetValue();
    if (targetValue == null) {
      return false;
    }
    return getViewProperty(singleProcessReportDefinitionRequestDto)
        .map(
            measure -> {
              if (measure.equals(ViewProperty.DURATION)) {
                return targetValue.getDurationProgress().getTarget().getIsBelow();
              } else {
                return targetValue.getCountProgress().getIsBelow();
              }
            })
        .orElse(false);
  }

  private Optional<ViewProperty> getViewProperty(
      final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    final List<ViewProperty> viewProperties =
        singleProcessReportDefinitionRequestDto.getData().getViewProperties();
    if (viewProperties.contains(ViewProperty.DURATION)) {
      return Optional.of(ViewProperty.DURATION);
    } else if (viewProperties.contains(ViewProperty.FREQUENCY)) {
      return Optional.of(ViewProperty.FREQUENCY);
    } else if (viewProperties.contains(ViewProperty.PERCENTAGE)) {
      return Optional.of(ViewProperty.PERCENTAGE);
    } else {
      return Optional.empty();
    }
  }

  private Optional<TargetAndUnit> getTargetAndUnit(
      final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    final SingleReportTargetValueDto targetValue =
        singleProcessReportDefinitionRequestDto.getData().getConfiguration().getTargetValue();
    return getViewProperty(singleProcessReportDefinitionRequestDto)
        .map(
            measure -> {
              if (measure.equals(ViewProperty.DURATION)) {
                final TargetDto targetDto = targetValue.getDurationProgress().getTarget();
                return Optional.of(new TargetAndUnit(targetDto.getValue(), targetDto.getUnit()));
              } else {
                return Optional.of(
                    new TargetAndUnit(targetValue.getCountProgress().getTarget(), null));
              }
            })
        .orElse(Optional.empty());
  }

  public List<SingleProcessReportDefinitionRequestDto> getValidKpiReportsForProcessDefinition(
      final String processDefinitionKey) {
    final List<SingleProcessReportDefinitionRequestDto> validKpis =
        reportService.getAllReportsForProcessDefinitionKeyOmitXml(processDefinitionKey).stream()
            .filter(SingleProcessReportDefinitionRequestDto.class::isInstance)
            .map(SingleProcessReportDefinitionRequestDto.class::cast)
            .filter(
                processReport ->
                    processReport.getData().getConfiguration().getTargetValue() != null
                        && processReport.getData().getConfiguration().getTargetValue().getIsKpi()
                            == Boolean.TRUE)
            // KPI reports should only have a single data source
            .filter(processReport -> processReport.getData().getDefinitions().size() == 1)
            .collect(Collectors.toList());
    validKpis.removeIf(processReport -> !ValidationHelper.isValid(processReport.getData()));
    return validKpis;
  }

  private static class TargetAndUnit {

    private String target;
    private TargetValueUnit targetValueUnit;

    public TargetAndUnit(final String target, final TargetValueUnit targetValueUnit) {
      this.target = target;
      this.targetValueUnit = targetValueUnit;
    }

    public String getTarget() {
      return target;
    }

    public void setTarget(final String target) {
      this.target = target;
    }

    public TargetValueUnit getTargetValueUnit() {
      return targetValueUnit;
    }

    public void setTargetValueUnit(final TargetValueUnit targetValueUnit) {
      this.targetValueUnit = targetValueUnit;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof TargetAndUnit;
    }

    @Override
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $target = getTarget();
      result = result * PRIME + ($target == null ? 43 : $target.hashCode());
      final Object $targetValueUnit = getTargetValueUnit();
      result = result * PRIME + ($targetValueUnit == null ? 43 : $targetValueUnit.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof TargetAndUnit)) {
        return false;
      }
      final TargetAndUnit other = (TargetAndUnit) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$target = getTarget();
      final Object other$target = other.getTarget();
      if (this$target == null ? other$target != null : !this$target.equals(other$target)) {
        return false;
      }
      final Object this$targetValueUnit = getTargetValueUnit();
      final Object other$targetValueUnit = other.getTargetValueUnit();
      if (this$targetValueUnit == null
          ? other$targetValueUnit != null
          : !this$targetValueUnit.equals(other$targetValueUnit)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "KpiService.TargetAndUnit(target="
          + getTarget()
          + ", targetValueUnit="
          + getTargetValueUnit()
          + ")";
    }
  }
}
