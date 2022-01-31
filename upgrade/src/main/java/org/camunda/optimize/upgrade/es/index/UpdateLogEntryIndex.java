/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es.index;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import org.elasticsearch.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_LONG;

@Component
public class UpdateLogEntryIndex extends DefaultIndexMappingCreator {
  public static final String INDEX_NAME = "update-log";
  public static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return INDEX_NAME;
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
      .endObject()
      ;
    // @formatter:on
  }

}
