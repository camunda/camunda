/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

import io.camunda.optimize.rest.exceptions.NotSupportedException;
import org.opensearch.client.opensearch.indices.IndexSettings;

public final class OptimizeOpenSearchUtil {

  private OptimizeOpenSearchUtil() {}

  public static IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder contentBuilder) {
    if (NUMBER_OF_SHARDS_SETTING.equalsIgnoreCase(key)) {
      return contentBuilder.numberOfShards(Integer.toString(value));
    } else {
      throw new NotSupportedException(
          "Cannot set property "
              + value
              + " for OpenSearch settings. Operation not "
              + "supported");
    }
  }
}
