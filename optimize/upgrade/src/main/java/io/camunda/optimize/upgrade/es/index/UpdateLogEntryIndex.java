/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es.index;

import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_PROPERTY_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_DATE;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_KEYWORD;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_LONG;
import static io.camunda.optimize.service.db.DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME;

import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

@Component
public class UpdateLogEntryIndex extends DefaultIndexMappingCreator<XContentBuilder> {

  public static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return UPDATE_LOG_ENTRY_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
        .startObject(UpgradeStepLogEntryDto.Fields.indexName)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .endObject()
        .startObject(UpgradeStepLogEntryDto.Fields.optimizeVersion)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .endObject()
        .startObject(UpgradeStepLogEntryDto.Fields.stepType)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .endObject()
        .startObject(UpgradeStepLogEntryDto.Fields.stepNumber)
        .field(MAPPING_PROPERTY_TYPE, TYPE_LONG)
        .endObject()
        .startObject(UpgradeStepLogEntryDto.Fields.appliedDate)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .endObject();
    // @formatter:on
  }

  @Override
  public XContentBuilder addStaticSetting(
      final String key, final int value, final XContentBuilder contentBuilder) throws IOException {
    return contentBuilder.field(key, value);
  }
}
