/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.schema.index;

import org.camunda.optimize.service.db.schema.index.CollectionIndex;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchUtil;
import org.opensearch.client.opensearch.indices.IndexSettings;

public class CollectionIndexOS extends CollectionIndex<IndexSettings.Builder> {

  @Override
  public IndexSettings.Builder addStaticSetting(final String key,
                                                final int value,
                                                final IndexSettings.Builder contentBuilder) {
    return OptimizeOpenSearchUtil.addStaticSetting(key, value, contentBuilder);
  }
}
