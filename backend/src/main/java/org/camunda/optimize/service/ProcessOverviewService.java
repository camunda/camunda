/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOwnerResponseDto;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.onboardinglistener.OnboardingNotificationService.MAGIC_LINK_TEMPLATE;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessOverviewService {

  public static final String APP_CUE_DASHBOARD_SUFFIX = "?appcue=7c293dbb-3957-4187-a079-f0237161c489";
  public static final String PENDING_OWNER_UPDATE_TEMPLATE = "pendingauthcheck#%s#%s";

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
            .orElse(new ProcessDigestDto(new AlertInterval(1, AlertIntervalUnit.WEEKS), false, new HashMap<>())),
          kpiService.getKpiResultsForProcessDefinition(procDefKey, timezone),
          magicLinkToDashboard
        );
      }).collect(Collectors.toList());
  }

  public Optional<ProcessOverviewDto> getProcessOverviewByKey(final String processDefinitionKey) {
    return processOverviewReader.getProcessOverviewByKey(processDefinitionKey);
  }

  private boolean collectionAlreadyCreatedForProcess(final String procDefKey) {
    try {
      return collectionService.getCollectionDefinition(procDefKey).isAutomaticallyCreated();
    } catch (NotFoundException e) {
      // Doesn't exist yet, return false
      return false;
    }
  }

  public void updateProcessOwner(final String userId, final String processDefKey, final String ownerId) {
    validateProcessDefinitionAuthorization(userId, processDefKey);
    String ownerIdToSave = resolveUserId(userId, ownerId);
    processOverviewWriter.upsertProcessOwner(processDefKey, ownerIdToSave);
  }

  @SneakyThrows
  private String resolveUserId(final String userId, final String ownerId) {
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
    return ownerIdToSave;
  }

  public void updateProcessOwnerIfNotSet(final String userId, final String processDefinitionKey, final String ownerId) {
    final Optional<ProcessOverviewDto> overviewForProcess = processOverviewReader.getProcessOverviewByKey(
      processDefinitionKey);
    overviewForProcess.ifPresentOrElse(processOverviewDto -> {
      String owner = Optional.ofNullable(processOverviewDto.getOwner()).orElse("");
      if (owner.isEmpty()) {
        processOverviewWriter.upsertProcessOwner(processDefinitionKey, ownerId);
      } else {
        log.info(String.format("Not updating process owner for process definition %s because owner has already been " +
                                 "set manually", processDefinitionKey));
      }
    }, () -> {
      try {
        updateProcessOwner(userId, processDefinitionKey, ownerId);
      } catch (NotFoundException e) {
        // If this happens, it means that Optimize did not import the process definition yet. So we save the
        // information but mark it as pending authorization check
        String ownerIdToSave = resolveUserId(userId, ownerId);
        log.info(String.format("Process definition %s has not been imported to optimize yet, so saving the " +
                                 "prospective owner %s as pending", processDefinitionKey, ownerIdToSave));
        String pendingProcessKey = String.format(PENDING_OWNER_UPDATE_TEMPLATE, userId, processDefinitionKey);
        processOverviewWriter.upsertProcessOwner(pendingProcessKey, ownerIdToSave);
      }
    });
  }

  public void confirmOrDenyOwnershipData(final String processToBeOnboarded) {
    Map<String, ProcessOverviewDto> pendingProcesses = processOverviewReader.getProcessOverviewsWithPendingOwnershipData();
    pendingProcesses
      .keySet()
      .stream()
      .filter(completeDefKey -> {
        Pattern pattern = Pattern.compile(String.format(PENDING_OWNER_UPDATE_TEMPLATE, "(.*)", "(.*)$"));
        return pattern.matcher(completeDefKey).matches();
      })
      .forEach(completeDefKey -> {
        String userIdFromRequester = extractUserIdFromPendingDefKey(completeDefKey);
        String ownerId = pendingProcesses.get(completeDefKey).getOwner();
        try {
          updateProcessOwner(userIdFromRequester, processToBeOnboarded, ownerId);
          removePendingEntryFromDatabase(completeDefKey);
        } catch (Exception exc) {
          log.warn(exc.getMessage(), exc);
        }
      });
  }

  private void removePendingEntryFromDatabase(final String completeDefKey) {
    processOverviewWriter.deleteProcessOwnerEntry(completeDefKey);
  }

  private String extractUserIdFromPendingDefKey(final String defKey) {
    Pattern pattern = Pattern.compile(String.format(PENDING_OWNER_UPDATE_TEMPLATE, "(.*)", "(.*)$"));
    Matcher matcher = pattern.matcher(defKey);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "";
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
