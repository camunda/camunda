/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service;


import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionAvailableVersionsWithTenants;
import org.camunda.optimize.service.collection.CollectionScopeService;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;

@Component
@Slf4j
public class ProcessDefinitionService extends AbstractDefinitionService {

  private final ProcessDefinitionReader processDefinitionReader;
  private final DefinitionService definitionService;
  private final CollectionScopeService collectionScopeService;

  public ProcessDefinitionService(final TenantService tenantService,
                                  final DefinitionAuthorizationService definitionAuthorizationService,
                                  final ProcessDefinitionReader processDefinitionReader,
                                  final DefinitionService definitionService,
                                  final CollectionScopeService collectionScopeService) {
    super(tenantService, definitionAuthorizationService);
    this.processDefinitionReader = processDefinitionReader;
    this.definitionService = definitionService;
    this.collectionScopeService = collectionScopeService;
  }

  public Optional<String> getProcessDefinitionXml(final String userId,
                                                  final String definitionKey,
                                                  final List<String> definitionVersions) {
    return getProcessDefinitionXml(userId, definitionKey, definitionVersions, (String) null);
  }

  public Optional<String> getProcessDefinitionXml(final String userId,
                                                  final String definitionKey,
                                                  final String version,
                                                  final String tenantId) {
    return getProcessDefinitionXml(
      userId,
      definitionKey,
      Collections.singletonList(version),
      Collections.singletonList(tenantId)
    );
  }

  public Optional<String> getProcessDefinitionXml(final String userId,
                                                  final String definitionKey,
                                                  final List<String> definitionVersions,
                                                  final String tenantId) {
    return getProcessDefinitionXml(userId, definitionKey, definitionVersions, Collections.singletonList(tenantId));
  }

  public Optional<String> getProcessDefinitionXml(final String userId,
                                                  final String definitionKey,
                                                  final List<String> definitionVersions,
                                                  final List<String> tenantIds) {
    return getProcessDefinitionWithXml(userId, definitionKey, definitionVersions, tenantIds)
      .map(ProcessDefinitionOptimizeDto::getBpmn20Xml);
  }


  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionWithXml(final String userId,
                                                                            final String definitionKey,
                                                                            final List<String> definitionVersions,
                                                                            final List<String> tenantIds) {
    return getProcessDefinitionWithXmlAsService(definitionKey, definitionVersions, tenantIds)
      .map(processDefinitionOptimizeDto -> {
        if (isAuthorizedToReadProcessDefinition(userId, processDefinitionOptimizeDto)) {
          return processDefinitionOptimizeDto;
        } else {
          throw new ForbiddenException("Current user is not authorized to access data of the process definition");
        }
      });
  }

  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionWithXmlAsService(final String definitionKey,
                                                                                     final String definitionVersion,
                                                                                     final String tenantId) {
    return getProcessDefinitionWithXmlAsService(
      definitionKey,
      Collections.singletonList(definitionVersion),
      Optional.ofNullable(tenantId)
        .map(Collections::singletonList)
        .orElse(Collections.emptyList())
    );
  }

  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionWithXmlAsService(final String definitionKey,
                                                                                     final List<String> definitionVersions,
                                                                                     final List<String> tenantIds) {
    if (definitionKey == null || definitionVersions == null || definitionVersions.isEmpty()) {
      return Optional.empty();
    }

    // first try to load tenant specific definition
    Optional<ProcessDefinitionOptimizeDto> fullyImportedDefinition =
      processDefinitionReader.getProcessDefinitionFromFirstTenantIfAvailable(
        definitionKey,
        definitionVersions,
        tenantIds
      );

    // if not available try to get shared definition
    if (!fullyImportedDefinition.isPresent()) {
      fullyImportedDefinition =
        processDefinitionReader.getProcessDefinitionFromFirstTenantIfAvailable(
          definitionKey,
          definitionVersions,
          Collections.emptyList()
        );
    }
    return fullyImportedDefinition;
  }

  public List<ProcessDefinitionOptimizeDto> getFullyImportedProcessDefinitions(final String userId, boolean withXml) {
    log.debug("Fetching process definitions");
    List<ProcessDefinitionOptimizeDto> definitionsResult = processDefinitionReader
      .getFullyImportedProcessDefinitions(withXml);

    if (userId != null) {
      definitionsResult = filterAuthorizedProcessDefinitions(userId, definitionsResult);
    }

    return definitionsResult;
  }

  public List<DefinitionAvailableVersionsWithTenants> getProcessDefinitionVersionsWithTenants(@NonNull final String userId) {
    return definitionService.getDefinitionsGroupedByVersionAndTenantForType(userId, PROCESS);
  }

  public List<DefinitionAvailableVersionsWithTenants> getProcessDefinitionVersionsWithTenants(@NonNull final String userId,
                                                                                              @NonNull final String collectionId) {
    final Map<String, List<String>> keysAndTenants = collectionScopeService
      .getAvailableKeysAndTenantsFromCollectionScope(userId, IdentityType.USER, collectionId);

    return definitionService.getDefinitionsGroupedByVersionAndTenantForType(userId, keysAndTenants, PROCESS);
  }

  private List<ProcessDefinitionOptimizeDto> filterAuthorizedProcessDefinitions(
    final String userId,
    final List<ProcessDefinitionOptimizeDto> processDefinitions) {
    return processDefinitions
      .stream()
      .filter(def -> def.getIsEventBased() || isAuthorizedToReadProcessDefinition(userId, def))
      .collect(toList());
  }

  private boolean isAuthorizedToReadProcessDefinition(final String userId,
                                                      final ProcessDefinitionOptimizeDto processDefinition) {
    return processDefinition.getIsEventBased() || definitionAuthorizationService.isUserAuthorizedToSeeProcessDefinition(
      userId, processDefinition.getKey(), processDefinition.getTenantId(), processDefinition.getEngine()
    );
  }

}
