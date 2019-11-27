/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.variable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.reader.DecisionVariableReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.TenantAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.util.ValidationHelper.ensureListNotEmpty;
import static org.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

@RequiredArgsConstructor
@Component
@Slf4j
public class DecisionVariableService {

  private final DecisionVariableReader decisionVariableReader;
  private final TenantAuthorizationService tenantAuthorizationService;
  private final DecisionDefinitionReader decisionDefinitionReader;


  public List<DecisionVariableNameDto> getInputVariableNames(String identityId,
                                                             DecisionVariableNameRequestDto variableRequestDto) {
    ensureNotEmpty("decision definition key", variableRequestDto.getDecisionDefinitionKey());
    ensureListNotEmpty("decision definition versions", variableRequestDto.getDecisionDefinitionVersions());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(identityId, IdentityType.USER, variableRequestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    final Optional<DecisionDefinitionOptimizeDto> decisionDefinition = getDecisionDefinition(variableRequestDto);
    return decisionDefinition.orElseThrow(() -> new OptimizeRuntimeException(
      "Could not extract input variables. Requested decision definition not found!")).getInputVariableNames();
  }

  public List<DecisionVariableNameDto> getOutputVariableNames(String userId,
                                                              DecisionVariableNameRequestDto variableRequestDto) {
    ensureNotEmpty("decision definition key", variableRequestDto.getDecisionDefinitionKey());
    ensureListNotEmpty("decision definition versions", variableRequestDto.getDecisionDefinitionVersions());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, IdentityType.USER, variableRequestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    final Optional<DecisionDefinitionOptimizeDto> decisionDefinition = getDecisionDefinition(variableRequestDto);
    return decisionDefinition.orElseThrow(() -> new OptimizeRuntimeException(
      "Could not extract input variables. Requested decision definition not found!")).getOutputVariableNames();
  }

  public List<String> getInputVariableValues(String userId, DecisionVariableValueRequestDto requestDto) {
    ensureNotEmpty("decision definition key", requestDto.getDecisionDefinitionKey());
    ensureListNotEmpty("decision definition versions", requestDto.getDecisionDefinitionVersions());
    ensureNotEmpty("variable id", requestDto.getVariableId());
    ensureNotEmpty("variable type", requestDto.getVariableType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, IdentityType.USER, requestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    return decisionVariableReader.getInputVariableValues(requestDto);
  }

  public List<String> getOutputVariableValues(String userId, DecisionVariableValueRequestDto requestDto) {
    ensureNotEmpty("decision definition key", requestDto.getDecisionDefinitionKey());
    ensureListNotEmpty("decision definition versions", requestDto.getDecisionDefinitionVersions());
    ensureNotEmpty("variable id", requestDto.getVariableId());
    ensureNotEmpty("variable type", requestDto.getVariableType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, IdentityType.USER, requestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    return decisionVariableReader.getOutputVariableValues(requestDto);
  }

  private Optional<DecisionDefinitionOptimizeDto> getDecisionDefinition(final DecisionVariableNameRequestDto variableRequestDto) {
    return variableRequestDto.getTenantIds().stream()
      .map(tenantId -> decisionDefinitionReader.getFullyImportedDecisionDefinition(
        variableRequestDto.getDecisionDefinitionKey(),
        variableRequestDto.getDecisionDefinitionVersions(),
        tenantId
      ))
      .filter(Optional::isPresent)
      .findFirst()
      .orElse(decisionDefinitionReader.getFullyImportedDecisionDefinition(
        variableRequestDto.getDecisionDefinitionKey(),
        variableRequestDto.getDecisionDefinitionVersions(),
        null
      ));
  }
}
