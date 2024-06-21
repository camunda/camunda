/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.relations;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class DashboardRelationService {

  private final List<DashboardReferencingService> referenceServices;

  @Lazy
  public DashboardRelationService(final List<DashboardReferencingService> referenceServices) {
    this.referenceServices = referenceServices;
  }

  public void handleDeleted(final DashboardDefinitionRestDto definition) {
    for (final DashboardReferencingService referencingService : referenceServices) {
      referencingService.handleDashboardDeleted(definition);
    }
  }

  public void handleUpdated(final DashboardDefinitionRestDto updateDefinition) {
    for (final DashboardReferencingService referencingService : referenceServices) {
      referencingService.handleDashboardUpdated(updateDefinition);
    }
  }
}
