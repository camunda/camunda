/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.relations;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class DashboardRelationService {

  private final List<DashboardReferencingService> referenceServices;

  @Lazy
  public DashboardRelationService(final List<DashboardReferencingService> referenceServices) {
    this.referenceServices = referenceServices;
  }

  public Set<ConflictedItemDto> getConflictedItemsForDelete(DashboardDefinitionDto definition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    for (DashboardReferencingService referencingService : referenceServices) {
      conflictedItems.addAll(referencingService.getConflictedItemsForDashboardDelete(definition));
    }
    return conflictedItems;
  }

  public Set<ConflictedItemDto> getConflictedItemsForUpdated(DashboardDefinitionDto currentDefinition,
                                                             DashboardDefinitionDto updateDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    for (DashboardReferencingService referencingService : referenceServices) {
      conflictedItems.addAll(referencingService.getConflictedItemsForDashboardUpdate(
        currentDefinition,
        updateDefinition
      ));
    }
    return conflictedItems;
  }

  public void handleDeleted(DashboardDefinitionDto definition) {
    for (DashboardReferencingService referencingService : referenceServices) {
      referencingService.handleDashboardDeleted(definition);
    }
  }

  public void handleUpdated(final String reportId,
                            final DashboardDefinitionDto updateDefinition) {
    for (DashboardReferencingService referencingService : referenceServices) {
      referencingService.handleDashboardUpdated(reportId, updateDefinition);
    }
  }
}
