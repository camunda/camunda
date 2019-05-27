/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service;


import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DecisionDefinitionGroupOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionAvailableVersionsWithTenants;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DecisionDefinitionService extends AbstractDefinitionService {

  private final DefinitionAuthorizationService authorizationService;
  private final DecisionDefinitionReader decisionDefinitionReader;

  public DecisionDefinitionService(final TenantService tenantService,
                                   final DefinitionAuthorizationService authorizationService,
                                   final DecisionDefinitionReader decisionDefinitionReader) {
    super(tenantService);
    this.authorizationService = authorizationService;
    this.decisionDefinitionReader = decisionDefinitionReader;
  }

  public Optional<String> getDecisionDefinitionXml(final String userId,
                                                   final String definitionKey,
                                                   final String definitionVersion,
                                                   final String tenantId) {
    // first try to load tenant specific definition
    Optional<DecisionDefinitionOptimizeDto> fullyImportedDefinition =
      decisionDefinitionReader.getFullyImportedDecisionDefinition(definitionKey, definitionVersion, tenantId);

    // if not available try to get shared definition
    if (!fullyImportedDefinition.isPresent()) {
      fullyImportedDefinition =
        decisionDefinitionReader.getFullyImportedDecisionDefinition(definitionKey, definitionVersion, null);
    }

    return fullyImportedDefinition
      .map(decisionDefinitionOptimizeDto -> {
        if (isAuthorizedToReadDecisionDefinition(userId, decisionDefinitionOptimizeDto)) {
          return decisionDefinitionOptimizeDto.getDmn10Xml();
        } else {
          throw new ForbiddenException("Current user is not authorized to access data of the decision definition");
        }
      });
  }

  public List<DecisionDefinitionOptimizeDto> getFullyImportedDecisionDefinitions(final String userId, boolean withXml) {
    log.debug("Fetching decision definitions");
    List<DecisionDefinitionOptimizeDto> definitionsResult = decisionDefinitionReader
      .getFullyImportedDecisionDefinitions(withXml);

    if (userId != null) {
      definitionsResult = filterAuthorizedDecisionDefinitions(userId, definitionsResult);
    }

    return definitionsResult;
  }

  public List<DecisionDefinitionGroupOptimizeDto> getDecisionDefinitionsGroupedByKey(final String userId) {
    Map<String, DecisionDefinitionGroupOptimizeDto> resultMap = getKeyToDecisionDefinitionMap(userId);
    return new ArrayList<>(resultMap.values());
  }

  public List<DefinitionAvailableVersionsWithTenants> getDecisionDefinitionVersionsWithTenants(final String userId) {
    final List<DecisionDefinitionOptimizeDto> definitions = getFullyImportedDecisionDefinitions(userId, false);
    return createDefinitionsWithAvailableVersionsAndTenants(userId, definitions);
  }

  private List<DecisionDefinitionOptimizeDto> filterAuthorizedDecisionDefinitions(
    final String userId,
    final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    return decisionDefinitions
      .stream()
      .filter(def -> isAuthorizedToReadDecisionDefinition(userId, def))
      .collect(Collectors.toList());
  }

  private boolean isAuthorizedToReadDecisionDefinition(final String userId,
                                                       final DecisionDefinitionOptimizeDto decisionDefinition) {
    return authorizationService.isAuthorizedToSeeDecisionDefinition(
      userId, decisionDefinition.getKey(), decisionDefinition.getTenantId()
    );
  }

  private Map<String, DecisionDefinitionGroupOptimizeDto> getKeyToDecisionDefinitionMap(final String userId) {
    final Map<String, DecisionDefinitionGroupOptimizeDto> resultMap = new HashMap<>();
    final List<DecisionDefinitionOptimizeDto> allDefinitions = getFullyImportedDecisionDefinitions(
      userId, false
    );
    for (DecisionDefinitionOptimizeDto process : allDefinitions) {
      String key = process.getKey();
      if (!resultMap.containsKey(key)) {
        resultMap.put(key, constructGroup(process));
      }
      resultMap.get(key).getVersions().add(process);
    }
    resultMap.values().forEach(DecisionDefinitionGroupOptimizeDto::sort);
    return resultMap;
  }

  private DecisionDefinitionGroupOptimizeDto constructGroup(final DecisionDefinitionOptimizeDto decision) {
    final DecisionDefinitionGroupOptimizeDto result = new DecisionDefinitionGroupOptimizeDto();
    result.setKey(decision.getKey());
    return result;
  }


}
