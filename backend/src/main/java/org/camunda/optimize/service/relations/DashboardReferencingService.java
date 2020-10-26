/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.relations;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;

public interface DashboardReferencingService {
  void handleDashboardDeleted(DashboardDefinitionRestDto definition);

  void handleDashboardUpdated(final DashboardDefinitionRestDto updateDefinition);
}
