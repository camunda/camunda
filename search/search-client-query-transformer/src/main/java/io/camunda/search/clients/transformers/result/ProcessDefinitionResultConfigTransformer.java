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
import io.camunda.search.result.ProcessDefinitionQueryResultConfig;
import java.util.List;

public final class ProcessDefinitionResultConfigTransformer
    implements ResultConfigTransformer<ProcessDefinitionQueryResultConfig> {

  @Override
  public SearchSourceConfig apply(final ProcessDefinitionQueryResultConfig value) {
    if (value != null) {
      final var builder = new SearchSourceFilter.Builder();

      if (!value.includeXml()) {
        builder.excludes(List.of("bpmnXml"));
      }

      return new SearchSourceConfig(builder.build());
    }
    return null;
  }
}
