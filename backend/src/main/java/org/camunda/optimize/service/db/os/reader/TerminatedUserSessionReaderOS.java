/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import org.camunda.optimize.service.db.reader.TerminatedUserSessionReader;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.core.GetRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;

import static org.camunda.optimize.service.db.DatabaseConstants.TERMINATED_USER_SESSION_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Conditional(OpenSearchCondition.class)
public class TerminatedUserSessionReaderOS extends TerminatedUserSessionReader {

  private final OptimizeOpenSearchClient osClient;

  @Override
  protected boolean sessionIdExists(final String sessionId) {
    GetRequest.Builder requestBuilder = new GetRequest.Builder()
      .index(TERMINATED_USER_SESSION_INDEX_NAME)
      .id(sessionId)
      .sourceIncludes(Collections.emptyList());

    String errorMessage = String.format("Was not able to fetch user session for ID [%s]", sessionId);

    return osClient.get(requestBuilder, TerminatedUserSessionDto.class, errorMessage).found();
  }

}
