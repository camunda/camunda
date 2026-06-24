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
import io.camunda.search.result.ProcessInstanceQueryResultConfig;
import java.util.List;

public final class ProcessInstanceResultConfigTransformer
    implements ResultConfigTransformer<ProcessInstanceQueryResultConfig> {

  @Override
  public SearchSourceConfig apply(final ProcessInstanceQueryResultConfig value) {
    if (value != null) {
      final var builder = new SearchSourceFilter.Builder();

      if (value.onlyKeys()) {
        builder.includes(List.of("key", "rootProcessInstanceKey"));
      }

      return new SearchSourceConfig(builder.build());
    }
    return null;
  }
}
