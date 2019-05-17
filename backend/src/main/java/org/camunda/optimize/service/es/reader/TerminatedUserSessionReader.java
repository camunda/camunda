/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TERMINATED_USER_SESSION_TYPE;

@RequiredArgsConstructor
@Component
@Slf4j
public class TerminatedUserSessionReader {

  private final RestHighLevelClient esClient;

  public boolean exists(final String sessionId) {
    log.debug("Fetching terminated user session with id [{}]", sessionId);
    try {
      final GetRequest sessionByIdRequest = new GetRequest(
        getOptimizeIndexAliasForType(TERMINATED_USER_SESSION_TYPE),
        TERMINATED_USER_SESSION_TYPE,
        sessionId
      );
      sessionByIdRequest.fetchSourceContext(FetchSourceContext.DO_NOT_FETCH_SOURCE);
      return esClient.get(sessionByIdRequest, RequestOptions.DEFAULT).isExists();
    } catch (Exception e) {
      throw new OptimizeRuntimeException("Was not able to check for terminated session existence!", e);
    }
  }
}
