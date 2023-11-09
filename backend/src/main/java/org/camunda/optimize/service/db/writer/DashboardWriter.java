/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import lombok.NonNull;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;

public interface DashboardWriter {

  String DEFAULT_DASHBOARD_NAME = "New Dashboard";

  IdResponseDto createNewDashboard(@NonNull final String userId,
                                   @NonNull final DashboardDefinitionRestDto dashboardDefinitionDto);

  IdResponseDto createNewDashboard(@NonNull final String userId,
                                   @NonNull final DashboardDefinitionRestDto dashboardDefinitionDto,
                                   @NonNull final String id);

  IdResponseDto saveDashboard(@NonNull final DashboardDefinitionRestDto dashboardDefinitionDto);

  void updateDashboard(DashboardDefinitionUpdateDto dashboard, String id);

  void removeReportFromDashboards(String reportId);

  void deleteDashboardsOfCollection(String collectionId);

  void deleteDashboard(String dashboardId);

  void deleteManagementDashboard();

}
