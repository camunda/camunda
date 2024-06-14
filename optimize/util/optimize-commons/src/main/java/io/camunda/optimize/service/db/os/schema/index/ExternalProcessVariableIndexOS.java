/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.os.schema.index;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchUtil;
import io.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import org.opensearch.client.opensearch.indices.IndexSegmentSort;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.SegmentSortOrder;

public class ExternalProcessVariableIndexOS
    extends ExternalProcessVariableIndex<IndexSettings.Builder> {

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder contentBuilder) {
    return OptimizeOpenSearchUtil.addStaticSetting(key, value, contentBuilder);
  }

  @Override
  public IndexSettings.Builder getStaticSettings(
      IndexSettings.Builder contentBuilder, ConfigurationService configurationService)
      throws IOException {
    final IndexSettings.Builder result =
        super.getStaticSettings(contentBuilder, configurationService);
    return result.sort(
        new IndexSegmentSort.Builder()
            .field(INGESTION_TIMESTAMP)
            .order(SegmentSortOrder.Desc)
            .build());
  }
}
