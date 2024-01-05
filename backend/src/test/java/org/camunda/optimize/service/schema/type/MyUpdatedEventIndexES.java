/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.schema.type;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.DEFAULT_SHARD_NUMBER;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;
import static org.camunda.optimize.service.db.schema.index.MetadataIndex.SCHEMA_VERSION;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;

@Slf4j
public class MyUpdatedEventIndexES extends MyUpdatedEventIndex<XContentBuilder> {

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    return xContentBuilder.field(NUMBER_OF_SHARDS_SETTING, DEFAULT_SHARD_NUMBER);
  }

}
