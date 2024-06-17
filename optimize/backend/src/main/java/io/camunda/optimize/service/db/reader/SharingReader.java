/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_SHARE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.REPORT_SHARE_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
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

  long getShareCount(final String indexName);

  default long getDashboardShareCount() {
    return getShareCount(DASHBOARD_SHARE_INDEX_NAME);
  }

  default long getReportShareCount() {
    return getShareCount(REPORT_SHARE_INDEX_NAME);
  }
}
