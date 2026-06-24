/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es.indices;

import co.elastic.clients.elasticsearch.indices.IndexSettings.Builder;
import io.camunda.optimize.upgrade.db.indices.UserTestWithTemplateUpdatedMappingIndex;
import java.io.IOException;

public class UserTestWithTemplateUpdatedMappingIndexES
    extends UserTestWithTemplateUpdatedMappingIndex<Builder> {

  @Override
  public Builder addStaticSetting(
      final String key, final int value, final Builder indexSettingsBuilder) throws IOException {
    return indexSettingsBuilder.numberOfShards(Integer.toString(value));
  }
}
