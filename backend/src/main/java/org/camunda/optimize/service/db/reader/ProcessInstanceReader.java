/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.PageResultDto;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

public interface ProcessInstanceReader {

  PageResultDto<String> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(final String processDefinitionKey,
                                                                                        final OffsetDateTime endDate,
                                                                                        final Integer limit);

  PageResultDto<String> getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(final String processDefinitionKey,
                                                                                       final OffsetDateTime endDate,
                                                                                       final Integer limit,
                                                                                       final PageResultDto<String> previousPage);

  PageResultDto<String> getFirstPageOfProcessInstanceIdsThatEndedBefore(final String processDefinitionKey,
                                                                        final OffsetDateTime endDate,
                                                                        final Integer limit);

  PageResultDto<String> getNextPageOfProcessInstanceIdsThatEndedBefore(final String processDefinitionKey,
                                                                       final OffsetDateTime endDate,
                                                                       final Integer limit,
                                                                       final PageResultDto<String> previousPage);

  Set<String> getExistingProcessDefinitionKeysFromInstances();

  Optional<String> getProcessDefinitionKeysForInstanceId(final String instanceId);

  boolean processDefinitionHasStartedInstances(final String processDefinitionKey);

}
