/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.schema.index;

import org.camunda.optimize.service.db.schema.index.ProcessInstanceArchiveIndex;
import org.camunda.optimize.service.os.OptimizeOpenSearchUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.opensearch.client.opensearch.indices.IndexSettings;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

public class ProcessInstanceArchiveIndexOS extends ProcessInstanceArchiveIndex<IndexSettings.Builder> {

  public ProcessInstanceArchiveIndexOS(final String instanceIndexKey) {
    super(instanceIndexKey);
  }

  @Override
  public IndexSettings.Builder addStaticSetting(final String key,
                                                final int value,
                                                final IndexSettings.Builder contentBuilder) {
    return OptimizeOpenSearchUtil.addStaticSetting(key, value, contentBuilder);
  }

  @Override
  public IndexSettings.Builder getStaticSettings(IndexSettings.Builder contentBuilder,
                                                 ConfigurationService configurationService) throws IOException {
    return addStaticSetting(NUMBER_OF_SHARDS_SETTING, 1, contentBuilder);
  }

}
