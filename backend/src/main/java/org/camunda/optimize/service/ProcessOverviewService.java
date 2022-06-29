/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.Data;
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
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.service.collection.CollectionService;
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
import static org.camunda.optimize.service.onboardinglistener.OnboardingNotificationService.MAGIC_LINK_TEMPLATE;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessOverviewService {

  public static final String APP_CUE_DASHBOARD_SUFFIX = "?appcue=7c293dbb-3957-4187-a079-f0237161c489";

  private final DefinitionService definitionService;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final ProcessOverviewWriter processOverviewWriter;
  private final ProcessOverviewReader processOverviewReader;
  private final AbstractIdentityService identityService;
  private final KpiService kpiService;
  private final ReportEvaluationService reportEvaluationService;
  private final CollectionService collectionService;

  public List<ProcessOverviewResponseDto> getAllProcessOverviews(final String userId, final ZoneId timezone) {
    final Map<String, String> procDefKeysAndName = definitionService.getAllDefinitionsWithTenants(PROCESS)
      .stream()
      .filter(def ->
                definitionAuthorizationService.isAuthorizedToAccessDefinition(userId, PROCESS, def.getKey(), def.getTenantIds()))
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
            if (singleProcessReportDefinitionRequestDto.getData().getGroupBy().equals(new NoneGroupByDto())) {
              @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST) final SingleReportEvaluationResult<Double> evaluationResult = (SingleReportEvaluationResult<Double>)
                reportEvaluationService.evaluateSavedReportWithAdditionalFilters(
                  userId,
                  timezone,
                  singleProcessReportDefinitionRequestDto.getId(),
                  null,
                  null
                ).getEvaluationResult();
              final Double evaluationValue = evaluationResult.getFirstCommandResult().getFirstMeasureData();
              if (evaluationValue != null) {
                KpiResponseDto responseDto = new KpiResponseDto();
                getTargetAndUnit(singleProcessReportDefinitionRequestDto)
                  .ifPresent(targetAndUnit -> {
                    responseDto.setTarget(targetAndUnit.getTarget());
                    responseDto.setUnit(targetAndUnit.getTargetValueUnit());
                  });
                responseDto.setReportId(singleProcessReportDefinitionRequestDto.getId());
                responseDto.setReportName(singleProcessReportDefinitionRequestDto.getName());
                responseDto.setBelow(getIsBelow(singleProcessReportDefinitionRequestDto));
                responseDto.setMeasure(geViewProperty(singleProcessReportDefinitionRequestDto).orElse(null));
                responseDto.setValue(evaluationValue.toString());
                responseDto.setType(getKpiType(singleProcessReportDefinitionRequestDto));
                kpis.add(responseDto);
              }
            }
          }
        }

        String appCueSuffix = collectionAlreadyCreatedForProcess(procDefKey) ? "" : APP_CUE_DASHBOARD_SUFFIX;
        String magicLinkToDashboard = String.format(MAGIC_LINK_TEMPLATE, procDefKey, procDefKey) + appCueSuffix;

        return new ProcessOverviewResponseDto(
          StringUtils.isEmpty(entry.getValue()) ? procDefKey : entry.getValue(),
          procDefKey,
          overviewForKey.flatMap(overview -> Optional.ofNullable(overview.getOwner())
            .map(owner -> new ProcessOwnerResponseDto(owner, identityService.getIdentityNameById(owner).orElse(owner)))
          ).orElse(new ProcessOwnerResponseDto()),
          overviewForKey.map(ProcessOverviewDto::getDigest)
            .orElse(new ProcessDigestDto()),
          kpis,
          magicLinkToDashboard
        );
      }).collect(Collectors.toList());
  }

  private boolean collectionAlreadyCreatedForProcess(final String procDefKey) {
    try {
      return collectionService.getCollectionDefinition(procDefKey).isAutomaticallyCreated();
    } catch (NotFoundException e) {
      // Doesn't exist yet, return false
      return false;
    }
  }

  private KpiType getKpiType(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    return geViewProperty(singleProcessReportDefinitionRequestDto)
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

  private Optional<ViewProperty> geViewProperty(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
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

  private boolean getIsBelow(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    SingleReportTargetValueDto targetValue = singleProcessReportDefinitionRequestDto.getData()
      .getConfiguration()
      .getTargetValue();
    return geViewProperty(singleProcessReportDefinitionRequestDto)
      .map(measure -> {
        if (measure.equals(ViewProperty.DURATION)) {
          return targetValue.getDurationProgress().getTarget().getIsBelow();
        } else {
          return targetValue.getCountProgress().getIsBelow();
        }
      }).orElse(false);
  }

  private Optional<TargetAndUnit> getTargetAndUnit(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
    SingleReportTargetValueDto targetValue =
      singleProcessReportDefinitionRequestDto.getData().getConfiguration().getTargetValue();
    return geViewProperty(singleProcessReportDefinitionRequestDto)
      .map(measure -> {
        if (measure.equals(ViewProperty.DURATION)) {
          final TargetDto targetDto = targetValue.getDurationProgress().getTarget();
          return Optional.of(new TargetAndUnit(targetDto.getValue(), targetDto.getUnit()));
        } else {
          return Optional.of(new TargetAndUnit(targetValue.getCountProgress().getTarget(), null));
        }
      }).orElse(Optional.empty());
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
    processOverviewReader.getProcessOverviewByKey(processDefinitionKey)
      .ifPresentOrElse(processOverview -> {
        if (!processOverview.getOwner().equals(userId)) {
          throw new ForbiddenException(String.format(
            "User [%s] is not authorized to update digest for process definition with key [%s]. " +
              "Only process owners are permitted to update process digest settings.",
            userId,
            processDefinitionKey
          ));
        }
      }, () -> {
        throw new NotFoundException("Process definition with key " + processDefinitionKey + " does not exist.");
      });
  }

  private void validateProcessDefinitionAuthorization(final String userId, final String processDefKey) {
    definitionService.getProcessDefinitionWithTenants(processDefKey)
      .ifPresentOrElse(definition -> {
        if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
          userId, PROCESS, definition.getKey(), definition.getTenantIds())) {
          throw new ForbiddenException("User is not authorized to access the process definition with key " + processDefKey);
        }
      }, () -> {
        throw new NotFoundException("Process definition with key " + processDefKey + " does not exist.");
      });
  }

  @Data
  @AllArgsConstructor
  private static class TargetAndUnit {
    private String target;
    private TargetValueUnit targetValueUnit;
  }

}
