/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.util.definition;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;

import java.util.List;
import java.util.Set;

public interface DataSourceDefinitionAuthorizationService {

  boolean isAuthorizedToAccessDefinition(final String userId, final DefinitionType type, final String definitionKey,
                                         final List<String> tenantIds);

  List<TenantDto> resolveAuthorizedTenantsForProcess(final String userId, final SimpleDefinitionDto definitionDto,
                                                     final List<String> tenantIds, final Set<String> engines);

  boolean isAuthorizedToAccessDefinition(final String userId, final String tenantId,
                                         final SimpleDefinitionDto definition);

  <T extends DefinitionOptimizeResponseDto> boolean isAuthorizedToAccessDefinition(final String userId,
                                                                                   final T definition);

}
