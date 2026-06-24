/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchUtil;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import org.opensearch.client.opensearch.indices.IndexSettings;

public class DecisionInstanceIndexOS extends DecisionInstanceIndex<IndexSettings.Builder> {

  public DecisionInstanceIndexOS(final String decisionDefinitionKey) {
    super(decisionDefinitionKey);
  }

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder contentBuilder) {
    return OptimizeOpenSearchUtil.addStaticSetting(key, value, contentBuilder);
  }

  @Override
  public IndexSettings.Builder getStaticSettings(
      final IndexSettings.Builder contentBuilder, final ConfigurationService configurationService)
      throws IOException {
    return addStaticSetting(
        NUMBER_OF_SHARDS_SETTING,
        configurationService.getOpenSearchConfiguration().getNumberOfShards(),
        contentBuilder);
  }
}
