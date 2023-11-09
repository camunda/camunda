/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SharingReader {

  Optional<ReportShareRestDto> getReportShare(String shareId);

  Optional<DashboardShareRestDto> findDashboardShare(String shareId);

  Optional<ReportShareRestDto> findShareForReport(String reportId);

  Optional<DashboardShareRestDto> findShareForDashboard(String dashboardId);

  Map<String, ReportShareRestDto> findShareForReports(List<String> reports);

  Map<String, DashboardShareRestDto> findShareForDashboards(List<String> dashboards);

  long getReportShareCount();

  long getDashboardShareCount();

}
