/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.service.db.reader.SharingReader;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class SharingReaderOS implements SharingReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public Optional<ReportShareRestDto> getReportShare(String shareId) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public Optional<DashboardShareRestDto> findDashboardShare(String shareId) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public Optional<ReportShareRestDto> findShareForReport(String reportId) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public Optional<DashboardShareRestDto> findShareForDashboard(String dashboardId) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public Map<String, ReportShareRestDto> findShareForReports(List<String> reports) {
    //todo will be handled in the OPT-7230
    return new HashMap<>();
  }

  @Override
  public Map<String, DashboardShareRestDto> findShareForDashboards(List<String> dashboards) {
    //todo will be handled in the  \n OPT-7230
    return new HashMap<>();
  }

  @Override
  public long getReportShareCount() {
    //todo will be handled in the OPT-7230
    return -1L;
  }

  @Override
  public long getDashboardShareCount() {
    //todo will be handled in the OPT-7230
    return -1L;
  }

}
