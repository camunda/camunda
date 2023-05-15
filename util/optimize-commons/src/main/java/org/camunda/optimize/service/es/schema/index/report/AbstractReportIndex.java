/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_BOOLEAN;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_TEXT;

public abstract class AbstractReportIndex extends DefaultIndexMappingCreator {

  public static final String ID = ReportDefinitionDto.Fields.id;
  public static final String NAME = ReportDefinitionDto.Fields.name;
  public static final String DESCRIPTION = ReportDefinitionDto.Fields.description;
  public static final String LAST_MODIFIED = ReportDefinitionDto.Fields.lastModified;
  public static final String CREATED = ReportDefinitionDto.Fields.created;
  public static final String OWNER = ReportDefinitionDto.Fields.owner;
  public static final String LAST_MODIFIER = ReportDefinitionDto.Fields.lastModifier;
  public static final String COLLECTION_ID = ReportDefinitionDto.Fields.collectionId;

  public static final String REPORT_TYPE = ReportDefinitionDto.Fields.reportType;
  public static final String COMBINED = ReportDefinitionDto.Fields.combined;
  public static final String DATA = ReportDefinitionDto.Fields.data;

  public static final String CONFIGURATION = SingleReportDataDto.Fields.configuration;
  public static final String XML = SingleReportConfigurationDto.Fields.xml;
  public static final String AGGREGATION_TYPES = SingleReportConfigurationDto.Fields.aggregationTypes;

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
     XContentBuilder newBuilder = xContentBuilder
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
      .startObject(COMBINED)
       .field(MAPPING_PROPERTY_TYPE, TYPE_BOOLEAN)
      .endObject();
     // @formatter:on
    newBuilder = addReportTypeSpecificFields(newBuilder);
    return newBuilder;
  }

  protected abstract XContentBuilder addReportTypeSpecificFields(XContentBuilder xContentBuilder) throws IOException;

}
