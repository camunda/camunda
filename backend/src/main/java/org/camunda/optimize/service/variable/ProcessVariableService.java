/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.variable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.service.es.reader.ProcessVariableReader;
import org.camunda.optimize.service.security.TenantAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.List;

import static org.camunda.optimize.service.util.ValidationHelper.*;

@RequiredArgsConstructor
@Component
@Slf4j
public class ProcessVariableService {

  private final ProcessVariableReader processVariableReader;
  private final TenantAuthorizationService tenantAuthorizationService;


  public List<ProcessVariableNameResponseDto> getVariableNames(String userId, ProcessVariableNameRequestDto variableRequestDto) {
    ensureNotEmpty("process definition key", variableRequestDto.getProcessDefinitionKey());
    ensureListNotEmpty("process definition versions", variableRequestDto.getProcessDefinitionVersions());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, variableRequestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    return processVariableReader.getVariableNames(variableRequestDto);
  }

  public List<String> getVariableValues(String userId, ProcessVariableValueRequestDto requestDto) {
    ensureNotEmpty("process definition key", requestDto.getProcessDefinitionKey());
    ensureListNotEmpty("process definition versions", requestDto.getProcessDefinitionVersions());
    ensureNotEmpty("variable name", requestDto.getName());
    ensureNotEmpty("variable type", requestDto.getType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, requestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    return processVariableReader.getVariableValues(requestDto);
  }
}
