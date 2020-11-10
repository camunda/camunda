/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.variable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.service.es.reader.DecisionVariableReader;
import org.camunda.optimize.service.security.TenantAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.List;

import static org.camunda.optimize.service.DefinitionService.prepareTenantListForDefinitionSearch;
import static org.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

@RequiredArgsConstructor
@Component
@Slf4j
public class DecisionVariableService {

  private final DecisionVariableReader decisionVariableReader;
  private final TenantAuthorizationService tenantAuthorizationService;


  public List<DecisionVariableNameResponseDto> getInputVariableNames(DecisionVariableNameRequestDto variableRequestDto) {
    ensureNotEmpty("decision definition key", variableRequestDto.getDecisionDefinitionKey());
    return decisionVariableReader.getInputVariableNames(
      variableRequestDto.getDecisionDefinitionKey(),
      variableRequestDto.getDecisionDefinitionVersions(),
      prepareTenantListForDefinitionSearch(variableRequestDto.getTenantIds())
    );
  }

  public List<DecisionVariableNameResponseDto> getOutputVariableNames(DecisionVariableNameRequestDto variableRequestDto) {
    ensureNotEmpty("decision definition key", variableRequestDto.getDecisionDefinitionKey());
    return decisionVariableReader.getOutputVariableNames(
      variableRequestDto.getDecisionDefinitionKey(),
      variableRequestDto.getDecisionDefinitionVersions(),
      prepareTenantListForDefinitionSearch(variableRequestDto.getTenantIds())
    );
  }

  public List<String> getInputVariableValues(String userId, DecisionVariableValueRequestDto requestDto) {
    ensureNotEmpty("decision definition key", requestDto.getDecisionDefinitionKey());
    ensureNotEmpty("variable id", requestDto.getVariableId());
    ensureNotEmpty("variable type", requestDto.getVariableType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, IdentityType.USER, requestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    return decisionVariableReader.getInputVariableValues(requestDto);
  }

  public List<String> getOutputVariableValues(String userId, DecisionVariableValueRequestDto requestDto) {
    ensureNotEmpty("decision definition key", requestDto.getDecisionDefinitionKey());
    ensureNotEmpty("variable id", requestDto.getVariableId());
    ensureNotEmpty("variable type", requestDto.getVariableType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, IdentityType.USER, requestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    return decisionVariableReader.getOutputVariableValues(requestDto);
  }
}
