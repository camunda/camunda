/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionAvailableVersionsWithTenants;
import org.camunda.optimize.service.collection.CollectionScopeService;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DecisionDefinitionService extends AbstractDefinitionService {

  private final DecisionDefinitionReader decisionDefinitionReader;
  private final CollectionScopeService collectionScopeService;

  public DecisionDefinitionService(final TenantService tenantService,
                                   final DefinitionAuthorizationService definitionAuthorizationService,
                                   final DecisionDefinitionReader decisionDefinitionReader,
                                   final CollectionScopeService collectionScopeService) {
    super(tenantService, definitionAuthorizationService);
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.collectionScopeService = collectionScopeService;
  }

  public Optional<String> getDecisionDefinitionXml(final String userId,
                                                   final String definitionKey,
                                                   final List<String> definitionVersions,
                                                   final String tenantId) {
    // first try to load tenant specific definition
    Optional<DecisionDefinitionOptimizeDto> fullyImportedDefinition =
      decisionDefinitionReader.getFullyImportedDecisionDefinition(definitionKey, definitionVersions, tenantId);

    // if not available try to get shared definition
    if (!fullyImportedDefinition.isPresent()) {
      fullyImportedDefinition =
        decisionDefinitionReader.getFullyImportedDecisionDefinition(definitionKey, definitionVersions, null);
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

  public List<DefinitionAvailableVersionsWithTenants> getDecisionDefinitionVersionsWithTenants(@NonNull final String userId) {
    List<DecisionDefinitionOptimizeDto> definitions = decisionDefinitionReader
      .getFullyImportedDecisionDefinitions(false);

    definitions = filterAuthorizedDecisionDefinitions(userId, definitions);

    return createDefinitionsWithAvailableVersionsAndTenants(userId, definitions);
  }

  public List<DefinitionAvailableVersionsWithTenants> getDecisionDefinitionVersionsWithTenants(@NonNull final String userId,
                                                                                               @NonNull final String collectionId) {
    final Map<String, List<String>> keysAndTenants = collectionScopeService
      .getAvailableKeysAndTenantsFromCollectionScope(userId, collectionId);

    List<DecisionDefinitionOptimizeDto> definitions = decisionDefinitionReader
      .getFullyImportedDecisionDefinitionsForKeys(false, keysAndTenants.keySet());

    definitions = filterAuthorizedDecisionDefinitions(userId, definitions);

    return createDefinitionsWithAvailableVersionsAndTenants(userId, definitions, keysAndTenants);
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
    return definitionAuthorizationService.isAuthorizedToSeeDecisionDefinition(
      userId, decisionDefinition.getKey(), decisionDefinition.getTenantId(), decisionDefinition.getEngine()
    );
  }

}
