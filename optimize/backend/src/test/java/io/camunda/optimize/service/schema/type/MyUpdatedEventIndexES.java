/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.schema.type;

import static io.camunda.optimize.service.db.DatabaseConstants.DEFAULT_SHARD_NUMBER;

import co.elastic.clients.elasticsearch.indices.IndexSettings;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import org.slf4j.Logger;

public class MyUpdatedEventIndexES extends MyUpdatedEventIndex<IndexSettings.Builder> {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MyUpdatedEventIndexES.class);

  @Override
  public IndexSettings.Builder getStaticSettings(
      final IndexSettings.Builder builder, final ConfigurationService configurationService)
      throws IOException {
    return builder.numberOfShards(Integer.toString(DEFAULT_SHARD_NUMBER));
  }
}
