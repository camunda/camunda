/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import java.io.IOException;
import java.util.List;

public interface InstantDashboardMetadataWriter {

  void saveInstantDashboard(InstantDashboardDataDto dashboardDataDto);

  List<String> deleteOutdatedTemplateEntriesAndGetExistingDashboardIds(List<Long> hashesAllowed)
      throws IOException;
}
