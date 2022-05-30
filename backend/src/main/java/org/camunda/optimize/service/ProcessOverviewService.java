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
import org.camunda.optimize.service.es.reader.ProcessOverviewReader;
import org.camunda.optimize.service.es.writer.ProcessOverviewWriter;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.Collections;
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

  public List<ProcessOverviewResponseDto> getAllProcessOverviews(final String userId) {
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
    return procDefKeysAndName.entrySet()
      .stream()
      .map(entry -> {
        final String procDefKey = entry.getKey();
        final Optional<ProcessOverviewDto> overviewForKey = Optional.ofNullable(processOverviewByKey.get(procDefKey));
        return new ProcessOverviewResponseDto(
          StringUtils.isEmpty(entry.getValue()) ? procDefKey : entry.getValue(),
          procDefKey,
          overviewForKey.flatMap(overview -> Optional.ofNullable(overview.getOwner())
            .map(owner -> new ProcessOwnerResponseDto(owner, identityService.getIdentityNameById(owner).orElse(owner)))
          ).orElse(new ProcessOwnerResponseDto()),
          overviewForKey.map(ProcessOverviewDto::getDigest)
            .orElse(new ProcessDigestDto())
        );
      }).collect(Collectors.toList());
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
