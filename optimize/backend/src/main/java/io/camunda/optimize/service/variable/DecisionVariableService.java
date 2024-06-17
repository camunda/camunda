/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.variable;

import static io.camunda.optimize.service.DefinitionService.prepareTenantListForDefinitionSearch;
import static io.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import io.camunda.optimize.service.db.reader.DecisionVariableReader;
import io.camunda.optimize.service.security.util.tenant.DataSourceTenantAuthorizationService;
import jakarta.ws.rs.ForbiddenException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class DecisionVariableService {

  private final DecisionVariableReader decisionVariableReader;
  private final DataSourceTenantAuthorizationService tenantAuthorizationService;

  public List<DecisionVariableNameResponseDto> getInputVariableNames(
      List<DecisionVariableNameRequestDto> variableRequestDtos) {
    return variableRequestDtos.stream()
        .flatMap(
            entry ->
                decisionVariableReader
                    .getInputVariableNames(
                        entry.getDecisionDefinitionKey(),
                        entry.getDecisionDefinitionVersions(),
                        prepareTenantListForDefinitionSearch(entry.getTenantIds()))
                    .stream())
        .distinct()
        .collect(Collectors.toList());
  }

  public List<DecisionVariableNameResponseDto> getOutputVariableNames(
      List<DecisionVariableNameRequestDto> variableRequestDtos) {
    return variableRequestDtos.stream()
        .flatMap(
            entry ->
                decisionVariableReader
                    .getOutputVariableNames(
                        entry.getDecisionDefinitionKey(),
                        entry.getDecisionDefinitionVersions(),
                        prepareTenantListForDefinitionSearch(entry.getTenantIds()))
                    .stream())
        .distinct()
        .collect(Collectors.toList());
  }

  public List<String> getInputVariableValues(
      String userId, DecisionVariableValueRequestDto requestDto) {
    ensureNotEmpty("decision definition key", requestDto.getDecisionDefinitionKey());
    ensureNotEmpty("variable id", requestDto.getVariableId());
    ensureNotEmpty("variable type", requestDto.getVariableType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(
        userId, IdentityType.USER, requestDto.getTenantIds())) {
      throw new ForbiddenException(
          "Current user is not authorized to access data of all provided tenants");
    }
    return decisionVariableReader.getInputVariableValues(requestDto);
  }

  public List<String> getOutputVariableValues(
      String userId, DecisionVariableValueRequestDto requestDto) {
    ensureNotEmpty("decision definition key", requestDto.getDecisionDefinitionKey());
    ensureNotEmpty("variable id", requestDto.getVariableId());
    ensureNotEmpty("variable type", requestDto.getVariableType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(
        userId, IdentityType.USER, requestDto.getTenantIds())) {
      throw new ForbiddenException(
          "Current user is not authorized to access data of all provided tenants");
    }
    return decisionVariableReader.getOutputVariableValues(requestDto);
  }
}
