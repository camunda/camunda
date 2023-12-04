/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.service.db.reader.ProcessOverviewReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ProcessOverviewReaderOS implements ProcessOverviewReader {

  @Override
  public Map<String, ProcessOverviewDto> getProcessOverviewsByKey(final Set<String> processDefinitionKeys) {
    //todo will be handled in the OPT-7230
    return new HashMap<>();
  }

  @Override
  public Optional<ProcessOverviewDto> getProcessOverviewByKey(final String processDefinitionKey) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public Map<String, ProcessDigestResponseDto> getAllActiveProcessDigestsByKey() {
    //todo will be handled in the OPT-7230
    return new HashMap<>();
  }

  @Override
  public Map<String, ProcessOverviewDto> getProcessOverviewsWithPendingOwnershipData() {
    //todo will be handled in the OPT-7230
    return new HashMap<>();
  }

}
