/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.TERMINATED_USER_SESSION_INDEX_NAME;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.reader.TerminatedUserSessionReader;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TerminatedUserSessionReaderES extends TerminatedUserSessionReader {

  private final OptimizeElasticsearchClient esClient;

  public TerminatedUserSessionReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  protected boolean sessionIdExists(final String sessionId) throws IOException {
    return esClient
        .get(
            OptimizeGetRequestBuilderES.of(
                g ->
                    g.optimizeIndex(esClient, TERMINATED_USER_SESSION_INDEX_NAME)
                        .id(sessionId)
                        .source(s -> s.fetch(false))),
            Object.class)
        .found();
  }
}
