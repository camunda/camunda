/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.reader;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.service.db.reader.TerminatedUserSessionReader;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.TERMINATED_USER_SESSION_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Conditional(ElasticSearchCondition.class)
public class TerminatedUserSessionReaderES extends TerminatedUserSessionReader {

  private final OptimizeElasticsearchClient esClient;

  @Override
  protected boolean sessionIdExists(final String sessionId) throws IOException {
    final GetRequest sessionByIdRequest = new GetRequest(TERMINATED_USER_SESSION_INDEX_NAME).id(sessionId);
    sessionByIdRequest.fetchSourceContext(FetchSourceContext.DO_NOT_FETCH_SOURCE);
    return esClient.get(sessionByIdRequest).isExists();
  }

}
