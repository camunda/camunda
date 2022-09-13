/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROPERTIES_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_DOUBLE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_OBJECT;

// TODO Delete this index class after 3.9.0 - https://jira.camunda.com/browse/OPT-6445
public class ProcessGoalIndex extends DefaultIndexMappingCreator {

  public static final int VERSION = 1;

  private static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  private static final String OWNER = "owner";
  private static final String DURATION_GOALS = "durationGoals";
  private static final String GOAL_TYPE = "type";
  private static final String PERCENTILE = "percentile";
  private static final String VALUE = "value";
  private static final String UNIT = "unit";

  @Override
  public String getIndexName() {
    return "process-goals";
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
      .startObject(DURATION_GOALS)
        .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
        .startObject(PROPERTIES_PROPERTY_TYPE)
          .startObject(GOAL_TYPE)
            .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
          .endObject()
          .startObject(PERCENTILE)
            .field(MAPPING_PROPERTY_TYPE, TYPE_DOUBLE)
          .endObject()
          .startObject(VALUE)
            .field(MAPPING_PROPERTY_TYPE, TYPE_DOUBLE)
          .endObject()
          .startObject(UNIT)
            .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }

}
