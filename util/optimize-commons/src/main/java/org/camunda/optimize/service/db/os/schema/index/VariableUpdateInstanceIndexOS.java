/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.schema.index;

import org.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.opensearch.client.opensearch.indices.IndexSegmentSort;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.SegmentSortOrder;

import java.io.IOException;

public class VariableUpdateInstanceIndexOS extends VariableUpdateInstanceIndex<IndexSettings.Builder> {

  @Override
  public IndexSettings.Builder addStaticSetting(final String key,
                                                final int value,
                                                final IndexSettings.Builder contentBuilder) {
    return OptimizeOpenSearchUtil.addStaticSetting(key, value, contentBuilder);
  }

  @Override
  public IndexSettings.Builder getStaticSettings(IndexSettings.Builder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    final IndexSettings.Builder result = super.getStaticSettings(xContentBuilder, configurationService);
    return result.sort(
      new IndexSegmentSort.Builder()
        .field(TIMESTAMP)
        .order(SegmentSortOrder.Asc)
      .build()
    );
  }
}
