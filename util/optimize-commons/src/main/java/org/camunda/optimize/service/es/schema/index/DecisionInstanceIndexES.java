/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

public class DecisionInstanceIndexES extends DecisionInstanceIndex<XContentBuilder> {

  public DecisionInstanceIndexES(final String decisionDefinitionKey) {
    super(decisionDefinitionKey);
  }

  @Override
  public XContentBuilder addStaticSetting(final String key,
                                          final int value,
                                          final XContentBuilder contentBuilder) throws IOException {
    return contentBuilder.field(key, value);
  }

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    return addStaticSetting(NUMBER_OF_SHARDS_SETTING, configurationService.getElasticSearchConfiguration().getNumberOfShards(), xContentBuilder);
  }

}
