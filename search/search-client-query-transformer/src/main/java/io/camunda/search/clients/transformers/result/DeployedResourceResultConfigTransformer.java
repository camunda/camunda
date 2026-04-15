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
import io.camunda.search.result.DeployedResourceQueryResultConfig;
import io.camunda.webapps.schema.descriptors.index.DeployedResourceIndex;
import java.util.List;

public final class DeployedResourceResultConfigTransformer
    implements ResultConfigTransformer<DeployedResourceQueryResultConfig> {

  @Override
  public SearchSourceConfig apply(final DeployedResourceQueryResultConfig value) {
    if (value != null) {
      final var builder = new SearchSourceFilter.Builder();

      if (!value.includeContent()) {
        builder.excludes(List.of(DeployedResourceIndex.RESOURCE_CONTENT));
      }

      return new SearchSourceConfig(builder.build());
    }
    return null;
  }
}
