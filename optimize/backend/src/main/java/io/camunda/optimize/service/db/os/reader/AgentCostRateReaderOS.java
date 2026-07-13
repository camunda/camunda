/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_COST_RATE_INDEX_NAME;

import io.camunda.optimize.dto.optimize.AgentCostRateConfigDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.reader.AgentCostRateReader;
import io.camunda.optimize.service.db.schema.index.AgentCostRateIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Objects;
import java.util.Optional;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class AgentCostRateReaderOS implements AgentCostRateReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentCostRateReaderOS.class);
  private final OptimizeOpenSearchClient osClient;

  public AgentCostRateReaderOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public Optional<AgentCostRateConfigDto> getConfig() {
    LOG.debug("Fetching agent cost rate config");
    final GetRequest.Builder getReqBuilder =
        new GetRequest.Builder().index(AGENT_COST_RATE_INDEX_NAME).id(AgentCostRateIndex.ID);
    final String errorMessage = "There was an error while reading the agent cost rate config.";
    final GetResponse<AgentCostRateConfigDto> getResponse =
        osClient.get(getReqBuilder, AgentCostRateConfigDto.class, errorMessage);
    if (getResponse.found() && Objects.nonNull(getResponse.source())) {
      return Optional.of(getResponse.source());
    }
    return Optional.empty();
  }
}
