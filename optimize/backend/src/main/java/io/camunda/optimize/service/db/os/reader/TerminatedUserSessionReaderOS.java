/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.TERMINATED_USER_SESSION_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.reader.TerminatedUserSessionReader;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collections;
import org.opensearch.client.opensearch.core.GetRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class TerminatedUserSessionReaderOS extends TerminatedUserSessionReader {

  private final OptimizeOpenSearchClient osClient;

  public TerminatedUserSessionReaderOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  protected boolean sessionIdExists(final String sessionId) {
    final GetRequest.Builder requestBuilder =
        new GetRequest.Builder()
            .index(TERMINATED_USER_SESSION_INDEX_NAME)
            .id(sessionId)
            .sourceIncludes(Collections.emptyList());

    final String errorMessage =
        String.format("Was not able to fetch user session for ID [%s]", sessionId);

    return osClient.get(requestBuilder, TerminatedUserSessionDto.class, errorMessage).found();
  }
}
