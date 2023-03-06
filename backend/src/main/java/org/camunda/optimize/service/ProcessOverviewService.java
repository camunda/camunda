/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOwnerResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.digest.DigestService;
import org.camunda.optimize.service.es.reader.ProcessOverviewReader;
import org.camunda.optimize.service.es.writer.ProcessOverviewWriter;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
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

@AllArgsConstructor
@Component
@Slf4j
public class ProcessOverviewService {

  private static final String PENDING_OWNER_UPDATE_TEMPLATE = "pendingauthcheck#%s#%s";

  private final DefinitionService definitionService;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final ProcessOverviewWriter processOverviewWriter;
  private final ProcessOverviewReader processOverviewReader;
  private final AbstractIdentityService identityService;
  private final KpiService kpiService;
  private final CollectionService collectionService;
  private final DigestService digestService;

  public List<ProcessOverviewResponseDto> getAllProcessOverviews(final String userId) {
    final Map<String, String> procDefKeysAndName = definitionService.getAllDefinitionsWithTenants(PROCESS)
      .stream()
      .filter(def -> !def.getIsEventProcess())
      .filter(
        def -> definitionAuthorizationService.isAuthorizedToAccessDefinition(
          userId,
          PROCESS,
          def.getKey(),
          def.getTenantIds()
        ))
      .peek(def -> {
        if (def.getName() == null || def.getName().isEmpty()) {
          def.setName(def.getKey());
        }
      })
      .collect(toMap(DefinitionWithTenantIdsDto::getKey, DefinitionWithTenantIdsDto::getName));

    final Map<String, ProcessOverviewDto> processOverviewByKey =
      processOverviewReader.getProcessOverviewsByKey(procDefKeysAndName.keySet());

    return procDefKeysAndName.entrySet()
      .stream()
      .map(entry -> {
        final String procDefKey = entry.getKey();
        final Optional<ProcessOverviewDto> overviewForKey = Optional.ofNullable(processOverviewByKey.get(procDefKey));
        return new ProcessOverviewResponseDto(
          entry.getValue(),
          procDefKey,
          overviewForKey.flatMap(overview -> Optional.ofNullable(overview.getOwner())
            .map(owner -> new ProcessOwnerResponseDto(owner, identityService.getIdentityNameById(owner).orElse(owner)))
          ).orElse(new ProcessOwnerResponseDto()),
          overviewForKey.map(ProcessOverviewDto::getDigest)
            .orElse(new ProcessDigestDto(false, new HashMap<>())),
          overviewForKey.map(kpiService::extractKpiResultsForProcessDefinition).orElse(Collections.emptyList())
        );
      }).collect(Collectors.toList());
  }

  public void updateProcess(final String userId, final String processDefKey, final ProcessUpdateDto processUpdateDto) {
    validateProcessDefinitionAuthorization(userId, processDefKey);
    final String ownerIdToSave = getValidatedOwnerId(userId, processUpdateDto.getOwnerId());
    processUpdateDto.setOwnerId(ownerIdToSave);
    if (processUpdateDto.getProcessDigest().isEnabled() && processUpdateDto.getOwnerId() == null) {
      throw new BadRequestException("Process digest cannot be enabled if no owner is set");
    }
    processOverviewWriter.updateProcessConfiguration(processDefKey, processUpdateDto);
    digestService.handleProcessUpdate(processDefKey, processUpdateDto);
  }

  public void updateProcessOwnerIfNotSet(final String userId, final String processDefinitionKey, final String ownerId) {
    final String ownerIdToSave = getValidatedOwnerId(userId, ownerId);
    if (ownerIdToSave == null || ownerIdToSave.isEmpty()) {
      throw new BadRequestException("Owner ID cannot be empty!");
    }
    if (definitionHasBeenImported(processDefinitionKey)) {
      log.info("Updating owner of process " + processDefinitionKey + " to " + ownerIdToSave);
      validateProcessDefinitionAuthorization(userId, processDefinitionKey);
      processOverviewWriter.updateProcessOwnerIfNotSet(processDefinitionKey, ownerIdToSave);
    } else {
      // If this happens, it means that Optimize did not import the process definition yet. So we save the
      // information but mark it as pending authorization check
      log.info(String.format("Process definition %s has not been imported to optimize yet, so saving the " +
                               "prospective owner %s as pending", processDefinitionKey, ownerIdToSave));
      String pendingProcessKey = String.format(PENDING_OWNER_UPDATE_TEMPLATE, userId, processDefinitionKey);
      processOverviewWriter.updateProcessOwnerIfNotSet(pendingProcessKey, ownerIdToSave);
    }
  }

  private String getValidatedOwnerId(final String userId, final String ownerId) {
    return Optional.ofNullable(ownerId)
      .map(owner -> {
        final Optional<String> ownerUserId = identityService.getUserById(owner).map(IdentityDto::getId);
        if (ownerUserId.isEmpty() || (!userId.equals(ownerUserId.get()) &&
          !identityService.isUserAuthorizedToAccessIdentity(userId, new IdentityDto(ownerId, IdentityType.USER)))) {
          throw new ForbiddenException(String.format(
            "Could not find a user with ID %s that the user %s is authorized to see.", owner, userId));
        }
        return ownerUserId.get();
      }).orElse(null);
  }

  private boolean definitionHasBeenImported(final String processDefinitionKey) {
    try {
      return definitionService.getLatestVersionToKey(PROCESS, processDefinitionKey) != null;
    } catch (NotFoundException exception) {
      log.info("Process with definition key {} has not yet been imported", processDefinitionKey);
      return false;
    }
  }

  public void confirmOrDenyOwnershipData(final String processToBeOnboarded) {
    Map<String, ProcessOverviewDto> pendingProcesses =
      processOverviewReader.getProcessOverviewsWithPendingOwnershipData();
    pendingProcesses
      .keySet()
      .stream()
      .filter(completeDefKey -> {
        Pattern pattern = Pattern.compile(
          String.format(PENDING_OWNER_UPDATE_TEMPLATE, "(.*)", processToBeOnboarded + "$"));
        return pattern.matcher(completeDefKey).matches();
      })
      .forEach(completeDefKey -> {
        String userIdFromRequester = extractUserIdFromPendingDefKey(completeDefKey).orElse(null);
        String ownerId = pendingProcesses.get(completeDefKey).getOwner();
        try {
          updateProcessOwnerIfNotSet(userIdFromRequester, processToBeOnboarded, ownerId);
          processOverviewWriter.deleteProcessOwnerEntry(completeDefKey);
        } catch (Exception exc) {
          log.warn(exc.getMessage(), exc);
        }
      });
  }

  private Optional<String> extractUserIdFromPendingDefKey(final String defKey) {
    Pattern pattern = Pattern.compile(String.format(PENDING_OWNER_UPDATE_TEMPLATE, "(.*)", "(.*)$"));
    Matcher matcher = pattern.matcher(defKey);
    if (matcher.find()) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  private void validateProcessDefinitionAuthorization(final String userId, final String processDefKey) {
    definitionService.getProcessDefinitionWithTenants(processDefKey)
      .ifPresentOrElse(definition -> {
        if (definition.getIsEventProcess() == Boolean.TRUE) {
          throw new BadRequestException("Event based processes cannot have owners nor digests configured");
        } else if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
          userId, PROCESS, definition.getKey(), definition.getTenantIds())) {
          throw new ForbiddenException("User is not authorized to access the process definition with key " + processDefKey);
        }
      }, () -> {
        throw new NotFoundException("Process definition with key " + processDefKey + " does not exist.");
      });
  }
}
