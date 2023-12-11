/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.service.db.reader.DashboardReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DashboardReaderOS implements DashboardReader {

  @Override
  public long getDashboardCount() {
    //todo will be handled in the OPT-7230
    return 0;
  }

  @Override
  public Optional<DashboardDefinitionRestDto> getDashboard(final String dashboardId) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboards(final Set<String> dashboardIds) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboardsForCollection(final String collectionId) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboardsForReport(final String reportId) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }
}
