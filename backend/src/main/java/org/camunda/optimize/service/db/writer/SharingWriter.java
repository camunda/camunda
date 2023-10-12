/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;

public interface SharingWriter {

  ReportShareRestDto saveReportShare(final ReportShareRestDto createSharingDto);

  DashboardShareRestDto saveDashboardShare(final DashboardShareRestDto createSharingDto);

  void updateDashboardShare(final DashboardShareRestDto updatedShare);

  void deleteReportShare(final String shareId);

  void deleteDashboardShare(final String shareId);

}
