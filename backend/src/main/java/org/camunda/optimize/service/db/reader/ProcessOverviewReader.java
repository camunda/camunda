/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ProcessOverviewReader {

  Map<String, ProcessOverviewDto> getProcessOverviewsByKey(final Set<String> processDefinitionKeys);

  Optional<ProcessOverviewDto> getProcessOverviewByKey(final String processDefinitionKey);

  Map<String, ProcessDigestResponseDto> getAllActiveProcessDigestsByKey();

  Map<String, ProcessOverviewDto> getProcessOverviewsWithPendingOwnershipData();
}
