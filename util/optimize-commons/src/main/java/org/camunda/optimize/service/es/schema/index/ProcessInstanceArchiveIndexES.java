/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.service.db.schema.index.ProcessInstanceArchiveIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

public class ProcessInstanceArchiveIndexES extends ProcessInstanceArchiveIndex<XContentBuilder> {

  public ProcessInstanceArchiveIndexES(final String instanceIndexKey) {
    super(instanceIndexKey);
  }

  @Override
  protected String getIndexPrefix() {
    return DatabaseConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX;
  }

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    return xContentBuilder.field(NUMBER_OF_SHARDS_SETTING, 1);
  }

  @Override
  public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder) throws IOException {
    return contentBuilder.field(key, value);
  }

}
