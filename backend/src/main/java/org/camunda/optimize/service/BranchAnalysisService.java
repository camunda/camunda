/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.camunda.optimize.service.es.reader.BranchAnalysisReader;
import org.camunda.optimize.service.security.EngineDefinitionAuthorizationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.time.ZoneId;

@RequiredArgsConstructor
@Component
public class BranchAnalysisService {

  private final EngineDefinitionAuthorizationService definitionAuthorizationService;
  private final BranchAnalysisReader branchAnalysisReader;

  public BranchAnalysisResponseDto branchAnalysis(final String userId,
                                                  final BranchAnalysisRequestDto request,
                                                  final ZoneId timezone) {
    ValidationHelper.validate(request);
    if (!definitionAuthorizationService.isAuthorizedToSeeProcessDefinition(
      userId, IdentityType.USER, request.getProcessDefinitionKey(), request.getTenantIds()
    )) {
      throw new ForbiddenException(
        "Current user is not authorized to access data of the provided process definition and tenant combination");
    }

    return branchAnalysisReader.branchAnalysis(request, timezone);
  }
}
