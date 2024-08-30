/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index.report;

import static io.camunda.optimize.service.db.DatabaseConstants.DYNAMIC_PROPERTY_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_ENABLED_SETTING;
import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_PROPERTY_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_OBJECT;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_TEXT;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class SingleDecisionReportIndex<TBuilder> extends AbstractReportIndex<TBuilder> {

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
  protected XContentBuilder addReportTypeSpecificFields(XContentBuilder xContentBuilder)
      throws IOException {
    // @formatter:off
    return xContentBuilder
        .startObject(DATA)
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
