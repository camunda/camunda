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
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOwnerResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.es.reader.ProcessOverviewReader;
import org.camunda.optimize.service.es.writer.ProcessOverviewWriter;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.time.ZoneId;
import java.util.Collections;
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
  private final CollectionService collectionService;

  public List<ProcessOverviewResponseDto> getAllProcessOverviews(final String userId, final ZoneId timezone) {
    final Map<String, String> procDefKeysAndName = definitionService.getAllDefinitionsWithTenants(PROCESS)
      .stream()
      .filter(def ->
                definitionAuthorizationService.isAuthorizedToAccessDefinition(
                  userId,
                  PROCESS,
                  def.getKey(),
                  def.getTenantIds()
                ))
      .collect(toMap(DefinitionWithTenantIdsDto::getKey, DefinitionWithTenantIdsDto::getName));

    final Map<String, ProcessOverviewDto> processOverviewByKey =
      processOverviewReader.getProcessOverviewsByKey(procDefKeysAndName.keySet());

    return procDefKeysAndName.entrySet()
      .stream()
      .map(entry -> {
        final String procDefKey = entry.getKey();
        String appCueSuffix = collectionAlreadyCreatedForProcess(procDefKey) ? "" : APP_CUE_DASHBOARD_SUFFIX;
        String magicLinkToDashboard = String.format(MAGIC_LINK_TEMPLATE, procDefKey, procDefKey) + appCueSuffix;
        final Optional<ProcessOverviewDto> overviewForKey = Optional.ofNullable(processOverviewByKey.get(procDefKey));
        return new ProcessOverviewResponseDto(
          StringUtils.isEmpty(entry.getValue()) ? procDefKey : entry.getValue(),
          procDefKey,
          overviewForKey.flatMap(overview -> Optional.ofNullable(overview.getOwner())
            .map(owner -> new ProcessOwnerResponseDto(owner, identityService.getIdentityNameById(owner).orElse(owner)))
          ).orElse(new ProcessOwnerResponseDto()),
          overviewForKey.map(ProcessOverviewDto::getDigest)
            .orElse(new ProcessDigestDto()),
          kpiService.getKpiResultsForProcessDefinition(procDefKey, timezone),
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
}
