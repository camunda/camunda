/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_COST_RATE_INDEX_NAME;

import io.camunda.optimize.dto.optimize.AgentCostRateConfigDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.schema.index.AgentCostRateIndex;
import io.camunda.optimize.service.db.writer.AgentCostRateWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class AgentCostRateWriterOS implements AgentCostRateWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentCostRateWriterOS.class);
  private final OptimizeOpenSearchClient osClient;

  public AgentCostRateWriterOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public void upsertConfig(final AgentCostRateConfigDto config) {
    LOG.debug("Writing agent cost rate config to OpenSearch");
    final IndexRequest.Builder<AgentCostRateConfigDto> indexRequestBuilder =
        new IndexRequest.Builder<AgentCostRateConfigDto>()
            .index(AGENT_COST_RATE_INDEX_NAME)
            .id(AgentCostRateIndex.ID)
            .document(config)
            .refresh(Refresh.True);

    final IndexResponse indexResponse = osClient.index(indexRequestBuilder);
    final Result result = indexResponse.result();
    if (result != Result.Created && result != Result.Updated && result != Result.NoOp) {
      final String message = "Could not write agent cost rate config to OpenSearch.";
      LOG.error(message);
      throw new OptimizeRuntimeException(message);
    }
  }
}
