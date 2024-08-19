/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.schema.type;

import static io.camunda.optimize.service.db.DatabaseConstants.DEFAULT_SHARD_NUMBER;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;
import static io.camunda.optimize.service.db.os.OptimizeOpenSearchUtil.addStaticSetting;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.slf4j.Logger;

public class MyUpdatedEventIndexOS extends MyUpdatedEventIndex<IndexSettings.Builder> {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(MyUpdatedEventIndexOS.class);

  @Override
  public IndexSettings.Builder getStaticSettings(
      final IndexSettings.Builder contentBuilder, final ConfigurationService configurationService) {
    return addStaticSetting(NUMBER_OF_SHARDS_SETTING, DEFAULT_SHARD_NUMBER, contentBuilder);
  }
}
