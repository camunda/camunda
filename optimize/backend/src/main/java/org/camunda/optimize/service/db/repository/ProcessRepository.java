/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository;

import java.util.Map;
import java.util.Set;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;

public interface ProcessRepository {
  Map<String, ProcessOverviewDto> getProcessOverviewsByKey(Set<String> processDefinitionKeys);

  Map<String, ProcessDigestResponseDto> getAllActiveProcessDigestsByKey();

  Map<String, ProcessOverviewDto> getProcessOverviewsWithPendingOwnershipData();
}
