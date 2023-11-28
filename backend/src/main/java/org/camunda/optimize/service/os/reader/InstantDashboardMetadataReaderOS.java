/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.reader.InstantDashboardMetadataReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class InstantDashboardMetadataReaderOS implements InstantDashboardMetadataReader {

  @Override
  public Optional<String> getInstantDashboardIdFor(final String processDefinitionKey, final String template) throws
                                                                                                             OptimizeRuntimeException {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

}
