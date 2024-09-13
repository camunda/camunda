/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices;

import static io.camunda.optimize.service.db.DatabaseConstants.DYNAMIC_PROPERTY_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_ENABLED_SETTING;
import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_PROPERTY_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_BOOLEAN;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_DATE;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_KEYWORD;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_OBJECT;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_TEXT;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class SingleProcessReportIndexV11 extends AbstractReportIndex<XContentBuilder> {

  public static final String MANAGEMENT_REPORT = ProcessReportDataDto.Fields.managementReport;
  public static final String INSTANT_PREVIEW_REPORT =
      ProcessReportDataDto.Fields.instantPreviewReport;

  public static final int VERSION = 11;

  @Override
  public String getIndexName() {
    return SINGLE_PROCESS_REPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    XContentBuilder newBuilder =
        xContentBuilder
            .startObject(ID)
            .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
            .endObject()
            .startObject(NAME)
            .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
            .endObject()
            .startObject(DESCRIPTION)
            .field(MAPPING_PROPERTY_TYPE, TYPE_TEXT)
            .field("index", false)
            .endObject()
            .startObject(LAST_MODIFIED)
            .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
            .field("format", OPTIMIZE_DATE_FORMAT)
            .endObject()
            .startObject(CREATED)
            .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
            .field("format", OPTIMIZE_DATE_FORMAT)
            .endObject()
            .startObject(OWNER)
            .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
            .endObject()
            .startObject(LAST_MODIFIER)
            .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
            .endObject()
            .startObject(COLLECTION_ID)
            .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
            .endObject()
            .startObject(REPORT_TYPE)
            .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
            .endObject()
            .startObject("combined")
            .field(MAPPING_PROPERTY_TYPE, TYPE_BOOLEAN)
            .endObject();
    // @formatter:on
    newBuilder = addReportTypeSpecificFields(newBuilder);
    return newBuilder;
  }

  @Override
  protected XContentBuilder addReportTypeSpecificFields(final XContentBuilder xContentBuilder)
      throws IOException {
    // @formatter:off
    return xContentBuilder
        .startObject(DATA)
        .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
        .field(DYNAMIC_PROPERTY_TYPE, true)
        .startObject("properties")
        .startObject(ProcessReportDataDto.Fields.view)
        .field(MAPPING_ENABLED_SETTING, false)
        .endObject()
        .startObject(ProcessReportDataDto.Fields.groupBy)
        .field(MAPPING_ENABLED_SETTING, false)
        .endObject()
        .startObject(ProcessReportDataDto.Fields.distributedBy)
        .field(MAPPING_ENABLED_SETTING, false)
        .endObject()
        .startObject(ProcessReportDataDto.Fields.filter)
        .field(MAPPING_ENABLED_SETTING, false)
        .endObject()
        .startObject(MANAGEMENT_REPORT)
        .field(MAPPING_PROPERTY_TYPE, TYPE_BOOLEAN)
        .endObject()
        .startObject(INSTANT_PREVIEW_REPORT)
        .field(MAPPING_PROPERTY_TYPE, TYPE_BOOLEAN)
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

  @Override
  public XContentBuilder addStaticSetting(
      final String key, final int value, final XContentBuilder contentBuilder) throws IOException {
    return contentBuilder.field(key, value);
  }
}
