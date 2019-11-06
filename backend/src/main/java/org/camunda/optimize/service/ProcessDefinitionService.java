/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service;


import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionAvailableVersionsWithTenants;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.convertToValidDefinitionVersion;

@Component
@Slf4j
public class ProcessDefinitionService extends AbstractDefinitionService {

  private final ProcessDefinitionReader processDefinitionReader;

  public ProcessDefinitionService(final TenantService tenantService,
                                  final DefinitionAuthorizationService definitionAuthorizationService,
                                  final ProcessDefinitionReader processDefinitionReader) {
    super(tenantService, definitionAuthorizationService);
    this.processDefinitionReader = processDefinitionReader;
  }

  public Optional<String> getProcessDefinitionXml(final String userId,
                                                  final String definitionKey,
                                                  final List<String> definitionVersions) {
    return getProcessDefinitionXml(userId, definitionKey, definitionVersions, null);
  }

  public Optional<String> getProcessDefinitionXml(final String userId,
                                                  final String definitionKey,
                                                  final List<String> definitionVersions,
                                                  final String tenantId) {
    String mostRecentValidVersion = convertToValidDefinitionVersion(
      definitionKey,
      definitionVersions,
      processDefinitionReader::getLatestVersionToKey
    );
    return getProcessDefinitionXml(userId, definitionKey, mostRecentValidVersion, tenantId);
  }

  public Optional<String> getProcessDefinitionXml(final String userId,
                                                   final String definitionKey,
                                                   final String definitionVersion,
                                                   final String tenantId) {
    return getProcessDefinitionXmlAsService(definitionKey, definitionVersion, tenantId)
      .map(processDefinitionOptimizeDto -> {
        if (isAuthorizedToReadProcessDefinition(userId, processDefinitionOptimizeDto)) {
          return processDefinitionOptimizeDto.getBpmn20Xml();
        } else {
          throw new ForbiddenException("Current user is not authorized to access data of the process definition");
        }
      });
  }

  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionXmlAsService(final String definitionKey,
                                                                                 final String definitionVersion,
                                                                                 final String tenantId) {
    // first try to load tenant specific definition
    Optional<ProcessDefinitionOptimizeDto> fullyImportedDefinition =
      processDefinitionReader.getFullyImportedProcessDefinition(definitionKey, definitionVersion, tenantId);

    // if not available try to get shared definition
    if (!fullyImportedDefinition.isPresent()) {
      fullyImportedDefinition =
        processDefinitionReader.getFullyImportedProcessDefinition(definitionKey, definitionVersion, null);
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

  public List<DefinitionAvailableVersionsWithTenants> getProcessDefinitionVersionsWithTenants(final String userId) {
    final List<ProcessDefinitionOptimizeDto> definitions = getFullyImportedProcessDefinitions(userId, false);
    return createDefinitionsWithAvailableVersionsAndTenants(userId, definitions);
  }


  private List<ProcessDefinitionOptimizeDto> filterAuthorizedProcessDefinitions(
    final String userId,
    final List<ProcessDefinitionOptimizeDto> processDefinitions) {
    return processDefinitions
      .stream()
      .filter(def -> isAuthorizedToReadProcessDefinition(userId, def))
      .collect(Collectors.toList());
  }

  private boolean isAuthorizedToReadProcessDefinition(final String userId,
                                                      final ProcessDefinitionOptimizeDto processDefinition) {
    return definitionAuthorizationService.isAuthorizedToSeeProcessDefinition(
      userId, processDefinition.getKey(), processDefinition.getTenantId(), processDefinition.getEngine()
    );
  }

}
