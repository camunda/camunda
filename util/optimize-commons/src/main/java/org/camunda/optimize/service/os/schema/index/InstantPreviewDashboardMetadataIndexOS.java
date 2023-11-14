/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.schema.index;

import jakarta.ws.rs.NotSupportedException;
import org.camunda.optimize.service.db.schema.index.InstantPreviewDashboardMetadataIndex;
import org.opensearch.client.opensearch.indices.IndexSettings;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

public class InstantPreviewDashboardMetadataIndexOS
  extends InstantPreviewDashboardMetadataIndex<IndexSettings.Builder> {

  @Override
  public IndexSettings.Builder addStaticSetting(final String key,
                                                final int value,
                                                final IndexSettings.Builder contentBuilder) {
    if (NUMBER_OF_SHARDS_SETTING.equalsIgnoreCase(key)) {
      return contentBuilder.numberOfShards(Integer.toString(value));
    } else {
      throw new NotSupportedException("Cannot set property " + value + " for OpenSearch settings. Operation not " +
                                          " " +
                                          "supported");
    }
  }
}
