/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.relations;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;

import java.util.Set;

public interface DashboardReferencingService {
  Set<ConflictedItemDto> getConflictedItemsForDashboardDelete(DashboardDefinitionDto definition);

  void handleDashboardDeleted(DashboardDefinitionDto definition);


  Set<ConflictedItemDto> getConflictedItemsForDashboardUpdate(DashboardDefinitionDto currentDefinition,
                                                              DashboardDefinitionDto updateDefinition);

  void handleDashboardUpdated(final String id, final DashboardDefinitionDto updateDefinition);
}
