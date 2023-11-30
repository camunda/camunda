/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.PageResultDto;
import org.camunda.optimize.service.db.reader.ProcessInstanceReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ProcessInstanceReaderOS implements ProcessInstanceReader {

  @Override
  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(final String processDefinitionKey, final OffsetDateTime endDate, final Integer limit) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public PageResultDto<String> getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(final String processDefinitionKey
    , final OffsetDateTime endDate, final Integer limit, final PageResultDto<String> previousPage) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatEndedBefore(final String processDefinitionKey,
                                                                               final OffsetDateTime endDate,
                                                                               final Integer limit) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public PageResultDto<String> getNextPageOfProcessInstanceIdsThatEndedBefore(final String processDefinitionKey,
                                                                              final OffsetDateTime endDate, final Integer limit
    , final PageResultDto<String> previousPage) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public Set<String> getExistingProcessDefinitionKeysFromInstances() {
    //todo will be handled in the OPT-7230
    return new HashSet<>();
  }

  @Override
  public Optional<String> getProcessDefinitionKeysForInstanceId(final String instanceId) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public boolean processDefinitionHasStartedInstances(final String processDefinitionKey) {
    //todo will be handled in the OPT-7230
    return false;
  }

}
