/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceArchiveIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class ProcessInstanceArchiveIndexES extends ProcessInstanceArchiveIndex<XContentBuilder> {

  public ProcessInstanceArchiveIndexES(final String instanceIndexKey) {
    super(instanceIndexKey);
  }

  @Override
  protected String getIndexPrefix() {
    return DatabaseConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX;
  }

  @Override
  public XContentBuilder getStaticSettings(
      XContentBuilder xContentBuilder, ConfigurationService configurationService)
      throws IOException {
    return xContentBuilder.field(NUMBER_OF_SHARDS_SETTING, 1);
  }

  @Override
  public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder)
      throws IOException {
    return contentBuilder.field(key, value);
  }
}
