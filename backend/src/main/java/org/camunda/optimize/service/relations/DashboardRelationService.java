/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.relations;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DashboardRelationService {

  private final List<DashboardReferencingService> referenceServices;

  @Lazy
  public DashboardRelationService(final List<DashboardReferencingService> referenceServices) {
    this.referenceServices = referenceServices;
  }

  public void handleDeleted(DashboardDefinitionRestDto definition) {
    for (DashboardReferencingService referencingService : referenceServices) {
      referencingService.handleDashboardDeleted(definition);
    }
  }

  public void handleUpdated(final DashboardDefinitionRestDto updateDefinition) {
    for (DashboardReferencingService referencingService : referenceServices) {
      referencingService.handleDashboardUpdated(updateDefinition);
    }
  }
}
