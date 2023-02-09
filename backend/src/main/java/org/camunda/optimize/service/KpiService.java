/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiResultDto;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiType;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.DeletedIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutingFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.MultipleVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NoIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonCanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonSuspendedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.SuspendedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.service.es.report.PlainReportEvaluationHandler;
import org.camunda.optimize.service.es.report.ReportEvaluationInfo;
import org.camunda.optimize.service.report.ReportService;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

@Component
@Slf4j
@AllArgsConstructor
public class KpiService {

  private final ReportService reportService;
  private final PlainReportEvaluationHandler reportEvaluationHandler;

  public List<String> getKpisForProcessDefinition(final String processDefinitionKey) {
    return reportService.getAllReportsForProcessDefinitionKeyOmitXml(processDefinitionKey).stream()
      .filter(SingleProcessReportDefinitionRequestDto.class::isInstance)
      .map(SingleProcessReportDefinitionRequestDto.class::cast)
      .filter(processReport -> processReport.getData().getConfiguration().getTargetValue().getIsKpi().equals(true))
      .map(ReportDefinitionDto::getId)
      .collect(toList());
  }

  public List<KpiResultDto> evaluateKpiReports(final String processDefinitionKey) {
    final List<SingleProcessReportDefinitionRequestDto> kpiReports = getKpiReportsForProcessDefinition(processDefinitionKey);
    final List<KpiResultDto> kpiResponseDtos = new ArrayList<>();
    for (SingleProcessReportDefinitionRequestDto report : kpiReports) {
      final SingleReportEvaluationResult<?> evaluationResult
        = (SingleReportEvaluationResult<?>) reportEvaluationHandler
        .evaluateReport(ReportEvaluationInfo.builder(report).timezone(ZoneId.systemDefault()).build())
        .getEvaluationResult();
      if (evaluationResult.getFirstCommandResult().getFirstMeasureData() instanceof Double
        || evaluationResult.getFirstCommandResult().getFirstMeasureData() == null) {
        final Double evaluationValue = (Double) evaluationResult.getFirstCommandResult().getFirstMeasureData();
        KpiResultDto kpiResponseDto = new KpiResultDto();
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

  public List<KpiResultDto> extractKpiResultsForProcessDefinition(final ProcessOverviewDto processOverviewDto) {
    final List<KpiResultDto> kpiResponseDtos = new ArrayList<>();
    final List<SingleProcessReportDefinitionRequestDto> kpiReports = getKpiReportsForProcessDefinition(
      processOverviewDto.getProcessDefinitionKey());
    final Map<String, String> lastKpiEvaluationResults =
      Optional.ofNullable(processOverviewDto.getLastKpiEvaluationResults()).orElse(Collections.emptyMap());
    for (SingleProcessReportDefinitionRequestDto report : kpiReports) {
      KpiResultDto kpiResponseDto = new KpiResultDto();
      if (lastKpiEvaluationResults.containsKey(report.getId())) {
        kpiResponseDto.setValue(lastKpiEvaluationResults.get(report.getId()));
        getTargetAndUnit(report)
          .ifPresent(targetAndUnit -> {
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

  private KpiType getKpiType(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    return getViewProperty(singleProcessReportDefinitionRequestDto)
      .filter(measure -> (ViewProperty.DURATION.equals(measure) || (ViewProperty.PERCENTAGE.equals(measure)
        && !containsQualityFilter(singleProcessReportDefinitionRequestDto))))
      .map(measure -> KpiType.TIME)
      .orElse(KpiType.QUALITY);
  }

  private boolean containsQualityFilter(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    return singleProcessReportDefinitionRequestDto.getData().getFilter()
      .stream()
      .anyMatch(processFilter -> ((processFilter instanceof FlowNodeStartDateFilterDto) ||
        (processFilter instanceof FlowNodeEndDateFilterDto) ||
        (processFilter instanceof VariableFilterDto) ||
        (processFilter instanceof MultipleVariableFilterDto) ||
        (processFilter instanceof ExecutedFlowNodeFilterDto) ||
        (processFilter instanceof ExecutingFlowNodeFilterDto) ||
        (processFilter instanceof CanceledFlowNodeFilterDto) ||
        (processFilter instanceof RunningInstancesOnlyFilterDto) ||
        (processFilter instanceof CompletedInstancesOnlyFilterDto) ||
        (processFilter instanceof CanceledInstancesOnlyFilterDto) ||
        (processFilter instanceof NonCanceledInstancesOnlyFilterDto) ||
        (processFilter instanceof SuspendedInstancesOnlyFilterDto) ||
        (processFilter instanceof NonSuspendedInstancesOnlyFilterDto) ||
        (processFilter instanceof FlowNodeDurationFilterDto) ||
        (processFilter instanceof OpenIncidentFilterDto) ||
        (processFilter instanceof DeletedIncidentFilterDto) ||
        (processFilter instanceof ResolvedIncidentFilterDto) ||
        (processFilter instanceof NoIncidentFilterDto) ||
        (processFilter instanceof RunningFlowNodesOnlyFilterDto) ||
        (processFilter instanceof CompletedFlowNodesOnlyFilterDto) ||
        (processFilter instanceof CanceledFlowNodesOnlyFilterDto) ||
        (processFilter instanceof CompletedOrCanceledFlowNodesOnlyFilterDto)));
  }

  private boolean getIsBelow(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    SingleReportTargetValueDto targetValue = singleProcessReportDefinitionRequestDto.getData()
      .getConfiguration()
      .getTargetValue();
    if (targetValue == null) {
      return false;
    }
    return getViewProperty(singleProcessReportDefinitionRequestDto)
      .map(measure -> {
        if (measure.equals(ViewProperty.DURATION)) {
          return targetValue.getDurationProgress().getTarget().getIsBelow();
        } else {
          return targetValue.getCountProgress().getIsBelow();
        }
      }).orElse(false);
  }

  private Optional<ViewProperty> getViewProperty(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    List<ViewProperty> viewProperties = singleProcessReportDefinitionRequestDto.getData().getViewProperties();
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

  private Optional<TargetAndUnit> getTargetAndUnit(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    SingleReportTargetValueDto targetValue =
      singleProcessReportDefinitionRequestDto.getData().getConfiguration().getTargetValue();
    return getViewProperty(singleProcessReportDefinitionRequestDto)
      .map(measure -> {
        if (measure.equals(ViewProperty.DURATION)) {
          final TargetDto targetDto = targetValue.getDurationProgress().getTarget();
          return Optional.of(new TargetAndUnit(targetDto.getValue(), targetDto.getUnit()));
        } else {
          return Optional.of(new TargetAndUnit(targetValue.getCountProgress().getTarget(), null));
        }
      }).orElse(Optional.empty());
  }

  private List<SingleProcessReportDefinitionRequestDto> getKpiReportsForProcessDefinition(final String processDefinitionKey) {
    return reportService.getAllReportsForProcessDefinitionKeyOmitXml(processDefinitionKey).stream()
      .filter(SingleProcessReportDefinitionRequestDto.class::isInstance)
      .map(SingleProcessReportDefinitionRequestDto.class::cast)
      .filter(processReport -> processReport.getData().getConfiguration().getTargetValue() != null
        && processReport.getData().getConfiguration().getTargetValue().getIsKpi() == Boolean.TRUE)
      .collect(toList());
  }

  @Data
  @AllArgsConstructor
  private static class TargetAndUnit {
    private String target;
    private TargetValueUnit targetValueUnit;
  }

}
