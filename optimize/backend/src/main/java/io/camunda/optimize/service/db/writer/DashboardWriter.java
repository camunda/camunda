/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;

public interface DashboardWriter {

  String DEFAULT_DASHBOARD_NAME = "New Dashboard";

  IdResponseDto createNewDashboard(
      final String userId, final DashboardDefinitionRestDto dashboardDefinitionDto);

  IdResponseDto createNewDashboard(
      final String userId,
      final DashboardDefinitionRestDto dashboardDefinitionDto,
      final String id);

  IdResponseDto saveDashboard(final DashboardDefinitionRestDto dashboardDefinitionDto);

  void updateDashboard(DashboardDefinitionUpdateDto dashboard, String id);

  void removeReportFromDashboards(String reportId);

  void deleteDashboardsOfCollection(String collectionId);

  void deleteDashboard(String dashboardId);

  void deleteManagementDashboard();
}
