/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.service.es.writer.ProcessOverviewWriter;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessOverviewService {

  private final DefinitionService definitionService;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final ProcessOverviewWriter processOverviewWriter;

  public void updateProcess(final String userId,
                            final String processDefKey) {
    checkAuthorizationToProcessDefinition(userId, processDefKey);
    final ProcessOverviewDto processOverviewDto = new ProcessOverviewDto();
    processOverviewDto.setProcessDefinitionKey(processDefKey);
    processOverviewDto.setOwner(null);
    processOverviewWriter.updateProcess(processOverviewDto);
  }

  private void checkAuthorizationToProcessDefinition(final String userId, final String processDefKey) {
    final Optional<DefinitionWithTenantIdsDto> definitionForKey =
      definitionService.getProcessDefinitionWithTenants(processDefKey);
    if (definitionForKey.isEmpty()) {
      throw new NotFoundException("Process definition with key " + processDefKey + " does not exist");
    }
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
      userId, PROCESS, definitionForKey.get().getKey(), definitionForKey.get().getTenantIds())) {
      throw new ForbiddenException("User is not authorized for process definition with key " + processDefKey);
    }
  }
}
