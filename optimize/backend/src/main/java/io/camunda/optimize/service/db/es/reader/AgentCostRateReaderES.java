/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_COST_RATE_INDEX_NAME;

import co.elastic.clients.elasticsearch.core.GetResponse;
import io.camunda.optimize.dto.optimize.AgentCostRateConfigDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.reader.AgentCostRateReader;
import io.camunda.optimize.service.db.schema.index.AgentCostRateIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AgentCostRateReaderES implements AgentCostRateReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentCostRateReaderES.class);
  private final OptimizeElasticsearchClient esClient;

  public AgentCostRateReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public Optional<AgentCostRateConfigDto> getConfig() {
    LOG.debug("Fetching agent cost rate config");
    try {
      final GetResponse<AgentCostRateConfigDto> getResponse =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g ->
                      g.optimizeIndex(esClient, AGENT_COST_RATE_INDEX_NAME)
                          .id(AgentCostRateIndex.ID)),
              AgentCostRateConfigDto.class);
      return getResponse.found() ? Optional.ofNullable(getResponse.source()) : Optional.empty();
    } catch (final IOException e) {
      final String errorMessage = "There was an error while reading the agent cost rate config.";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }
}
