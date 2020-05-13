/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.camunda.optimize.service.es.reader.AssigneeAndCandidateGroupsReader;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.List;

import static org.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

@RequiredArgsConstructor
@Slf4j
@Component
public class AssigneeService {

  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader;

  public List<String> getAllAssignees(String userId, AssigneeRequestDto requestDto) {
    ensureNotEmpty("process definition key", requestDto.getProcessDefinitionKey());

    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
      userId,
      DefinitionType.PROCESS,
      requestDto.getProcessDefinitionKey(),
      requestDto.getTenantIds()
    )) {
      throw new ForbiddenException("Current user is not authorized to access data of the provided definition or tenants");
    }
    return assigneeAndCandidateGroupsReader.getAssignees(requestDto);
  }
}
