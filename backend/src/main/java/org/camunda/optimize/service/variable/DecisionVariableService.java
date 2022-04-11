/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.variable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.service.es.reader.DecisionVariableReader;
import org.camunda.optimize.service.security.util.tenant.DataSourceTenantAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.DefinitionService.prepareTenantListForDefinitionSearch;
import static org.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

@RequiredArgsConstructor
@Component
@Slf4j
public class DecisionVariableService {

  private final DecisionVariableReader decisionVariableReader;
  private final DataSourceTenantAuthorizationService tenantAuthorizationService;


  public List<DecisionVariableNameResponseDto> getInputVariableNames(List<DecisionVariableNameRequestDto> variableRequestDtos) {
    return variableRequestDtos.stream()
      .flatMap(entry -> decisionVariableReader
        .getInputVariableNames(
          entry.getDecisionDefinitionKey(),
          entry.getDecisionDefinitionVersions(),
          prepareTenantListForDefinitionSearch(entry.getTenantIds())
        ).stream()
      )
      .distinct()
      .collect(Collectors.toList());
  }

  public List<DecisionVariableNameResponseDto> getOutputVariableNames(List<DecisionVariableNameRequestDto> variableRequestDtos) {
    return variableRequestDtos.stream()
      .flatMap(entry -> decisionVariableReader
        .getOutputVariableNames(
          entry.getDecisionDefinitionKey(),
          entry.getDecisionDefinitionVersions(),
          prepareTenantListForDefinitionSearch(entry.getTenantIds())
        ).stream()
      )
      .distinct()
      .collect(Collectors.toList());
  }

  public List<String> getInputVariableValues(String userId, DecisionVariableValueRequestDto requestDto) {
    ensureNotEmpty("decision definition key", requestDto.getDecisionDefinitionKey());
    ensureNotEmpty("variable id", requestDto.getVariableId());
    ensureNotEmpty("variable type", requestDto.getVariableType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, IdentityType.USER,
                                                                requestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    return decisionVariableReader.getInputVariableValues(requestDto);
  }

  public List<String> getOutputVariableValues(String userId, DecisionVariableValueRequestDto requestDto) {
    ensureNotEmpty("decision definition key", requestDto.getDecisionDefinitionKey());
    ensureNotEmpty("variable id", requestDto.getVariableId());
    ensureNotEmpty("variable type", requestDto.getVariableType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, IdentityType.USER,
                                                                requestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    return decisionVariableReader.getOutputVariableValues(requestDto);
  }
}
