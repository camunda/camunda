/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiType;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOwnerResponseDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
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
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.SuspendedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.service.es.reader.ProcessOverviewReader;
import org.camunda.optimize.service.es.writer.ProcessOverviewWriter;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.report.ReportEvaluationService;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.camunda.optimize.util.SuppressionConstants;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessOverviewService {

  private final DefinitionService definitionService;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final ProcessOverviewWriter processOverviewWriter;
  private final ProcessOverviewReader processOverviewReader;
  private final AbstractIdentityService identityService;
  private final KpiService kpiService;
  private final ReportEvaluationService reportEvaluationService;

  public List<ProcessOverviewResponseDto> getAllProcessOverviews(final String userId, final ZoneId timezone) {
    final Map<String, String> procDefKeysAndName = definitionService.getAllDefinitionsWithTenants(PROCESS)
      .stream()
      .filter(def ->
                definitionAuthorizationService.isAuthorizedToAccessDefinition(
                  userId,
                  PROCESS,
                  def.getKey(),
                  def.getTenantIds()
                )
      )
      .collect(toMap(DefinitionWithTenantIdsDto::getKey, DefinitionWithTenantIdsDto::getName));

    final Map<String, ProcessOverviewDto> processOverviewByKey =
      processOverviewReader.getProcessOverviewsByKey(procDefKeysAndName.keySet());

    final Map<String, List<SingleProcessReportDefinitionRequestDto>> kpiReportsByKey = new HashMap<>();
    procDefKeysAndName.keySet()
      .forEach(processDefinitionKey -> kpiReportsByKey.put(
        processDefinitionKey,
        kpiService.getKpiReportsForProcessDefinition(processDefinitionKey)
      ));

    return procDefKeysAndName.entrySet()
      .stream()
      .map(entry -> {
        final String procDefKey = entry.getKey();
        final Optional<ProcessOverviewDto> overviewForKey = Optional.ofNullable(processOverviewByKey.get(procDefKey));
        final Optional<List<SingleProcessReportDefinitionRequestDto>> kpiReportsForKey = Optional.ofNullable(
          kpiReportsByKey.get(procDefKey));
        List<KpiResponseDto> kpis = new ArrayList<>();
        if (kpiReportsForKey.isPresent()) {
          for (SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto :
            kpiReportsForKey.get()) {
            @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST) final SingleReportEvaluationResult<Double> evaluationResult = (SingleReportEvaluationResult<Double>)
              reportEvaluationService.evaluateSavedReportWithAdditionalFilters(
                userId,
                timezone,
                singleProcessReportDefinitionRequestDto.getId(),
                null,
                null
              ).getEvaluationResult();
            final Double evaluationValue = evaluationResult.getFirstCommandResult().getFirstMeasureData();
            if(!String.valueOf(evaluationValue).equals("null")){
              KpiResponseDto responseDto = new KpiResponseDto();
              List<String> targetAndUnit = getTargetAndUnit(singleProcessReportDefinitionRequestDto);
              if(targetAndUnit != null) {
                responseDto.setTarget(targetAndUnit.get(0));
                responseDto.setUnit(targetAndUnit.get(1));
              }
              responseDto.setReportId(singleProcessReportDefinitionRequestDto.getId());
              responseDto.setReportName(singleProcessReportDefinitionRequestDto.getName());
              responseDto.setIsBelow(getIsBelow(singleProcessReportDefinitionRequestDto));
              responseDto.setMeasure(getMeasure(singleProcessReportDefinitionRequestDto));
              responseDto.setValue(evaluationValue.toString());
              responseDto.setType(getReportType(singleProcessReportDefinitionRequestDto));
              kpis.add(responseDto);
            }
          }
        }

        return new ProcessOverviewResponseDto(
          StringUtils.isEmpty(entry.getValue()) ? procDefKey : entry.getValue(),
          procDefKey,
          overviewForKey.flatMap(overview -> Optional.ofNullable(overview.getOwner())
            .map(owner -> new ProcessOwnerResponseDto(owner, identityService.getIdentityNameById(owner).orElse(owner)))
          ).orElse(new ProcessOwnerResponseDto()),
          overviewForKey.map(ProcessOverviewDto::getDigest)
            .orElse(new ProcessDigestDto()),
          kpis
        );
      }).collect(Collectors.toList());
  }

  private KpiType getReportType(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    ViewProperty viewProperty = getMeasure(singleProcessReportDefinitionRequestDto);
    if (ViewProperty.DURATION.equals(viewProperty)) {
      return KpiType.TIME;
    } else if (ViewProperty.PERCENTAGE.equals(viewProperty) && timeKpiFilters(
      singleProcessReportDefinitionRequestDto)) {
      return KpiType.TIME;
    } else {
      return KpiType.QUALITY;
    }
  }

  private boolean timeKpiFilters(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    boolean timeKpiFilters = false;
    for (ProcessFilterDto<?> processFilter : singleProcessReportDefinitionRequestDto.getData().getFilter()) {
      if ((processFilter instanceof FlowNodeStartDateFilterDto) ||
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
        (processFilter instanceof CompletedOrCanceledFlowNodesOnlyFilterDto)) {
        return timeKpiFilters;
      } else {
        timeKpiFilters = true;
      }
    }
    return timeKpiFilters;
  }

  private ViewProperty getMeasure(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    List<ViewProperty> viewProperties = singleProcessReportDefinitionRequestDto.getData().getViewProperties();
    if (viewProperties.contains(ViewProperty.DURATION)) {
      return ViewProperty.DURATION;
    } else if (viewProperties.contains(ViewProperty.FREQUENCY)) {
      return ViewProperty.FREQUENCY;
    } else if (viewProperties.contains(ViewProperty.PERCENTAGE)) {
      return ViewProperty.PERCENTAGE;
    } else {
      return null;
    }
  }

  private boolean getIsBelow(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    SingleReportTargetValueDto targetValue = singleProcessReportDefinitionRequestDto.getData()
      .getConfiguration()
      .getTargetValue();
    ViewProperty viewProperty = getMeasure(singleProcessReportDefinitionRequestDto);
    if (viewProperty == null) {
      return false;
    } else if (viewProperty.equals(ViewProperty.DURATION)) {
      return targetValue.getDurationProgress().getTarget().getIsBelow();
    } else {
      return targetValue.getCountProgress().getIsBelow();
    }
  }

  private List<String> getTargetAndUnit(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    List<String> targetAndUnit = new ArrayList<>();
    SingleReportTargetValueDto targetValue = singleProcessReportDefinitionRequestDto.getData()
      .getConfiguration()
      .getTargetValue();
    ViewProperty viewProperty = getMeasure(singleProcessReportDefinitionRequestDto);
    if (viewProperty == null) {
      return null;
    } else if (viewProperty.equals(ViewProperty.DURATION)) {
      targetAndUnit.add(0,targetValue.getDurationProgress().getTarget().getValue());
      targetAndUnit.add(1,targetValue.getDurationProgress().getTarget().getUnit().toString());
      return targetAndUnit;
    } else {
      targetAndUnit.add(0,targetValue.getCountProgress().getTarget());
      targetAndUnit.add(1,"");
      return targetAndUnit;
    }
  }

  public void updateProcessOwner(final String userId, final String processDefKey, final String ownerId) {
    validateProcessDefinitionAuthorization(userId, processDefKey);
    String ownerIdToSave = null;
    if (ownerId != null) {
      final Optional<String> ownerUserId = identityService.getUserById(ownerId).map(IdentityDto::getId);
      if (ownerUserId.isEmpty() ||
        !identityService.isUserAuthorizedToAccessIdentity(userId, new IdentityDto(ownerId, IdentityType.USER))) {
        throw new NotFoundException(String.format(
          "Could not find a user with ID %s that the user %s is authorized to see.", ownerId, userId));
      } else {
        ownerIdToSave = ownerUserId.get();
      }
    }
    processOverviewWriter.upsertProcessOwner(processDefKey, ownerIdToSave);
  }

  public void updateProcessDigest(final String userId,
                                  final String processDefKey,
                                  final ProcessDigestRequestDto digestToCreate) {
    validateProcessDefinitionAuthorization(userId, processDefKey);
    validateIsAuthorizedToUpdateDigest(userId, processDefKey);
    processOverviewWriter.updateProcessDigest(
      processDefKey,
      new ProcessDigestDto(
        digestToCreate.getCheckInterval(),
        digestToCreate.getEnabled(),
        Collections.emptyMap()
      )
    );
  }

  private void validateIsAuthorizedToUpdateDigest(final String userId,
                                                  final String processDefinitionKey) {
    final Optional<ProcessOverviewDto> processOverview =
      processOverviewReader.getProcessOverviewByKey(processDefinitionKey);
    if (processOverview.isEmpty() || !processOverview.get().getOwner().equals(userId)) {
      throw new ForbiddenException(String.format(
        "User [%s] is not authorized to update digest for process definition with key [%s]. " +
          "Only process owners are permitted to update process digest settings.",
        userId,
        processDefinitionKey
      )
      );
    }
  }

  private void validateProcessDefinitionAuthorization(final String userId, final String processDefKey) {
    final Optional<DefinitionWithTenantIdsDto> definitionForKey =
      definitionService.getProcessDefinitionWithTenants(processDefKey);
    if (definitionForKey.isEmpty()) {
      throw new NotFoundException("Process definition with key " + processDefKey + " does not exist.");
    }
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
      userId, PROCESS, definitionForKey.get().getKey(), definitionForKey.get().getTenantIds())) {
      throw new ForbiddenException("User is not authorized to access the process definition with key " + processDefKey);
    }
  }
}
