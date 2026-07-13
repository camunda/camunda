/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_COST_RATE_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import io.camunda.optimize.dto.optimize.AgentCostRateConfigDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.schema.index.AgentCostRateIndex;
import io.camunda.optimize.service.db.writer.AgentCostRateWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AgentCostRateWriterES implements AgentCostRateWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentCostRateWriterES.class);
  private final OptimizeElasticsearchClient esClient;

  public AgentCostRateWriterES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public void upsertConfig(final AgentCostRateConfigDto config) {
    LOG.debug("Writing agent cost rate config to Elasticsearch");
    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, AGENT_COST_RATE_INDEX_NAME)
                          .id(AgentCostRateIndex.ID)
                          .document(config)
                          .refresh(Refresh.True)));
      final Result result = indexResponse.result();
      if (result != Result.Created && result != Result.Updated && result != Result.NoOp) {
        final String message = "Could not write agent cost rate config to Elasticsearch.";
        LOG.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (final IOException e) {
      final String errorMessage = "There was an error while writing the agent cost rate config.";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }
}
