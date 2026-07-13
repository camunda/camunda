/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_COST_RATE_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.AgentCostRateConfigDto;
import io.camunda.optimize.dto.optimize.AgentCostRateConfigDto.AgentCostRateDto;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

/**
 * Single-document index (fixed id {@link #ID}) holding the user-configured LLM cost rates. Read at
 * import time to stamp per-instance cost; read/written by the {@code /api/agent-cost-rates}
 * endpoint.
 */
public abstract class AgentCostRateIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {
  public static final int VERSION = 1;
  public static final String ID = "1";

  public static final String CURRENCY = AgentCostRateConfigDto.Fields.currency.name();
  public static final String UNIT = AgentCostRateConfigDto.Fields.unit.name();
  public static final String RATES = AgentCostRateConfigDto.Fields.rates.name();

  public static final String MODEL = AgentCostRateDto.Fields.model.name();
  public static final String INPUT_RATE = AgentCostRateDto.Fields.inputRatePer1k.name();
  public static final String OUTPUT_RATE = AgentCostRateDto.Fields.outputRatePer1k.name();
  public static final String EFFECTIVE_FROM = AgentCostRateDto.Fields.effectiveFrom.name();

  @Override
  public String getIndexName() {
    return AGENT_COST_RATE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(CURRENCY, p -> p.keyword(k -> k))
        .properties(UNIT, p -> p.keyword(k -> k))
        .properties(
            RATES,
            p ->
                // The config is read whole by id and never aggregated, so a plain object (not
                // nested) mapping is sufficient.
                p.object(
                    o ->
                        o.properties(MODEL, pp -> pp.keyword(k -> k))
                            .properties(INPUT_RATE, pp -> pp.double_(k -> k))
                            .properties(OUTPUT_RATE, pp -> pp.double_(k -> k))
                            .properties(EFFECTIVE_FROM, pp -> pp.keyword(k -> k))));
  }
}
