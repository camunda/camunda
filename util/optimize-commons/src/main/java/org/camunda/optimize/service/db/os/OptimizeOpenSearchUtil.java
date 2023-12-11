/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os;

import jakarta.ws.rs.NotSupportedException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.opensearch.client.opensearch.indices.IndexSettings;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OptimizeOpenSearchUtil {

  public static IndexSettings.Builder addStaticSetting(final String key,
                                                       final int value,
                                                       final IndexSettings.Builder contentBuilder) {
    if (NUMBER_OF_SHARDS_SETTING.equalsIgnoreCase(key)) {
      return contentBuilder.numberOfShards(Integer.toString(value));
    } else {
      throw new NotSupportedException("Cannot set property " + value + " for OpenSearch settings. Operation not " +
                                        "supported");
    }
  }
}
