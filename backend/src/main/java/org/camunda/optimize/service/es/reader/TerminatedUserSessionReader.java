/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TERMINATED_USER_SESSION_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
public class TerminatedUserSessionReader {

  private final OptimizeElasticsearchClient esClient;

  public boolean exists(final String sessionId) {
    log.debug("Fetching terminated user session with id [{}]", sessionId);
    try {
      final GetRequest sessionByIdRequest = new GetRequest(TERMINATED_USER_SESSION_INDEX_NAME).id(sessionId);
      sessionByIdRequest.fetchSourceContext(FetchSourceContext.DO_NOT_FETCH_SOURCE);
      return esClient.get(sessionByIdRequest).isExists();
    } catch (Exception e) {
      throw new OptimizeRuntimeException("Was not able to check for terminated session existence!", e);
    }
  }
}
