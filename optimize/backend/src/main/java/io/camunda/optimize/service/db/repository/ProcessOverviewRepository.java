/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import java.util.List;

public interface ProcessOverviewRepository {
  void updateKpisForProcessDefinitions(List<ProcessOverviewDto> processOverviewDtos);

  void updateProcessConfiguration(String processDefinitionKey, ProcessOverviewDto overviewDto);

  void updateProcessDigestResults(String processDefKey, ProcessDigestDto processDigestDto);

  void updateProcessOwnerIfNotSet(
      String processDefinitionKey, String ownerId, ProcessOverviewDto processOverviewDto);

  void deleteProcessOwnerEntry(String processDefinitionKey);
}
