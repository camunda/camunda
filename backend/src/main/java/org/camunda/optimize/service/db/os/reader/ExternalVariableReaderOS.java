/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.db.reader.ExternalVariableReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ExternalVariableReaderOS implements ExternalVariableReader {

  @Override
  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAfter(final Long ingestTimestamp, final int limit) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAt(final Long ingestTimestamp) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

}
