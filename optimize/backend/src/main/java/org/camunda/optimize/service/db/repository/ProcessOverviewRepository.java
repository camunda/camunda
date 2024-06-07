/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository;

import java.util.List;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;

public interface ProcessOverviewRepository {
  void updateKpisForProcessDefinitions(List<ProcessOverviewDto> processOverviewDtos);

  void updateProcessConfiguration(String processDefinitionKey, ProcessOverviewDto overviewDto);

  void updateProcessDigestResults(String processDefKey, ProcessDigestDto processDigestDto);

  void updateProcessOwnerIfNotSet(
      String processDefinitionKey, String ownerId, ProcessOverviewDto processOverviewDto);

  void deleteProcessOwnerEntry(String processDefinitionKey);
}
