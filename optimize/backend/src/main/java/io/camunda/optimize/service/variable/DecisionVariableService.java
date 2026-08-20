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
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.db.reader.DecisionVariableReader;
import io.camunda.optimize.service.security.util.tenant.DataSourceTenantAuthorizationService;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class DecisionVariableService {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(DecisionVariableService.class);
  private final DecisionVariableReader decisionVariableReader;
  private final DataSourceTenantAuthorizationService tenantAuthorizationService;

  public DecisionVariableService(
      final DecisionVariableReader decisionVariableReader,
      final DataSourceTenantAuthorizationService tenantAuthorizationService) {
    this.decisionVariableReader = decisionVariableReader;
    this.tenantAuthorizationService = tenantAuthorizationService;
  }

  public List<DecisionVariableNameResponseDto> getInputVariableNames(
      final List<DecisionVariableNameRequestDto> variableRequestDtos) {
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
      final List<DecisionVariableNameRequestDto> variableRequestDtos) {
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
      final String userId, final DecisionVariableValueRequestDto requestDto) {
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
      final String userId, final DecisionVariableValueRequestDto requestDto) {
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
