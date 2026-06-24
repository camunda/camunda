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
import io.camunda.search.result.DecisionRequirementsQueryResultConfig;
import java.util.List;

public final class DecisionRequirementsResultConfigTransformer
    implements ResultConfigTransformer<DecisionRequirementsQueryResultConfig> {

  @Override
  public SearchSourceConfig apply(final DecisionRequirementsQueryResultConfig value) {
    if (value != null) {
      final var builder = new SearchSourceFilter.Builder();

      if (!value.includeXml()) {
        builder.excludes(List.of("xml"));
      }

      return new SearchSourceConfig(builder.build());
    }
    return null;
  }
}
