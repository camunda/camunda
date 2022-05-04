/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.ProcessOverviewDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;

public class ProcessOverviewIndex extends DefaultIndexMappingCreator {
  public static final int VERSION = 1;

  private static final String PROCESS_DEFINITION_KEY = ProcessOverviewDto.Fields.processDefinitionKey;
  private static final String OWNER = ProcessOverviewDto.Fields.owner;
  private static final String NAME = ProcessOverviewDto.Fields.name;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.PROCESS_OVERVIEW_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(PROCESS_DEFINITION_KEY)
      .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(OWNER)
      .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(NAME)
      .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject();
    // @formatter:on
  }
}
