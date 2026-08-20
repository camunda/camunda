/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema.index;

import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.SegmentSortOrder;
import io.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;

public class ExternalProcessVariableIndexES
    extends ExternalProcessVariableIndex<IndexSettings.Builder> {

  @Override
  public IndexSettings.Builder getStaticSettings(
      final IndexSettings.Builder builder, final ConfigurationService configurationService)
      throws IOException {

    final IndexSettings.Builder newXContentBuilder =
        super.getStaticSettings(builder, configurationService);
    return newXContentBuilder.sort(s -> s.field(INGESTION_TIMESTAMP).order(SegmentSortOrder.Desc));
  }

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder builder) throws IOException {
    return builder.numberOfShards(Integer.toString(value));
  }
}
