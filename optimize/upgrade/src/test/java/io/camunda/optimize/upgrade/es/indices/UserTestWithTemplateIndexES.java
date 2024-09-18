/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es.indices;

import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexSettings.Builder;
import io.camunda.optimize.upgrade.indices.UserTestWithTemplateIndex;
import java.io.IOException;

public class UserTestWithTemplateIndexES extends UserTestWithTemplateIndex<Builder> {

  public UserTestWithTemplateIndexES() {}

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder contentBuilder)
      throws IOException {
    return contentBuilder.numberOfShards(Integer.toString(value));
  }
}
