/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class DecisionInstanceIndexES extends DecisionInstanceIndex<XContentBuilder> {

  public DecisionInstanceIndexES(final String decisionDefinitionKey) {
    super(decisionDefinitionKey);
  }

  @Override
  public XContentBuilder addStaticSetting(
      final String key, final int value, final XContentBuilder contentBuilder) throws IOException {
    return contentBuilder.field(key, value);
  }

  @Override
  public XContentBuilder getStaticSettings(
      XContentBuilder xContentBuilder, ConfigurationService configurationService)
      throws IOException {
    return addStaticSetting(
        NUMBER_OF_SHARDS_SETTING,
        configurationService.getElasticSearchConfiguration().getNumberOfShards(),
        xContentBuilder);
  }
}
