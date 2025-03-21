/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es.index;

import co.elastic.clients.elasticsearch.indices.IndexSettings;
import org.springframework.stereotype.Component;

@Component
public class UpdateLogEntryIndexES
    extends io.camunda.optimize.upgrade.db.index.UpdateLogEntryIndex<IndexSettings.Builder> {

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder builder) {
    return builder.numberOfShards(Integer.toString(value));
  }
}
