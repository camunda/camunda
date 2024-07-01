/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;

public interface SharingWriter {

  ReportShareRestDto saveReportShare(final ReportShareRestDto createSharingDto);

  DashboardShareRestDto saveDashboardShare(final DashboardShareRestDto createSharingDto);

  void updateDashboardShare(final DashboardShareRestDto updatedShare);

  void deleteReportShare(final String shareId);

  void deleteDashboardShare(final String shareId);
}
