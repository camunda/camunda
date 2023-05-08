/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index.report;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DYNAMIC_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_OBJECT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_TEXT;

public class SingleDecisionReportIndex extends AbstractReportIndex {

  public static final int VERSION = 10;

  @Override
  public String getIndexName() {
    return SINGLE_DECISION_REPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  protected XContentBuilder addReportTypeSpecificFields(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder.
      startObject(DATA)
        .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
        .field(DYNAMIC_PROPERTY_TYPE, true)
        .startObject("properties")
          .startObject(DecisionReportDataDto.Fields.view)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(DecisionReportDataDto.Fields.groupBy)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(DecisionReportDataDto.Fields.distributedBy)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(DecisionReportDataDto.Fields.filter)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(CONFIGURATION)
            .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
            .field(DYNAMIC_PROPERTY_TYPE, true)
            .startObject("properties")
              .startObject(XML)
                .field(MAPPING_PROPERTY_TYPE, TYPE_TEXT)
                .field("index", true)
                .field("analyzer", "is_present_analyzer")
              .endObject()
              .startObject(AGGREGATION_TYPES)
                .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
                .field(DYNAMIC_PROPERTY_TYPE, true)
              .endObject()
            .endObject()
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }
}
