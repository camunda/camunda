/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.result;

import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.clients.source.SearchSourceFilter;
import io.camunda.search.result.DecisionInstanceQueryResultConfig;
import java.util.ArrayList;
import java.util.List;

public final class DecisionInstanceResultConfigTransformer
    implements ResultConfigTransformer<DecisionInstanceQueryResultConfig> {

  @Override
  public SearchSourceConfig apply(final DecisionInstanceQueryResultConfig value) {
    if (value != null) {
      final List<String> exclusions = new ArrayList<>();

      if (!value.includeEvaluatedInputs()) {
        exclusions.add("evaluatedInputs");
      }
      if (!value.includeEvaluatedOutputs()) {
        exclusions.add("evaluatedOutputs");
      }

      return new SearchSourceConfig(new SearchSourceFilter.Builder().excludes(exclusions).build());
    }
    return null;
  }
}
