/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.schema.type;

import static org.camunda.optimize.service.db.DatabaseConstants.DEFAULT_SHARD_NUMBER;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;
import static org.camunda.optimize.service.db.os.OptimizeOpenSearchUtil.addStaticSetting;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.opensearch.client.opensearch.indices.IndexSettings;

@Slf4j
public class MyUpdatedEventIndexOS extends MyUpdatedEventIndex<IndexSettings.Builder> {
  @Override
  public IndexSettings.Builder getStaticSettings(
      IndexSettings.Builder contentBuilder, ConfigurationService configurationService) {
    return addStaticSetting(NUMBER_OF_SHARDS_SETTING, DEFAULT_SHARD_NUMBER, contentBuilder);
  }
}
