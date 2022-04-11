/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index.report;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public abstract class AbstractReportIndex extends DefaultIndexMappingCreator {

  public static final String ID = "id";
  public static final String NAME = "name";
  public static final String LAST_MODIFIED = "lastModified";
  public static final String CREATED = "created";
  public static final String OWNER = "owner";
  public static final String LAST_MODIFIER = "lastModifier";
  public static final String COLLECTION_ID = "collectionId";

  public static final String REPORT_TYPE = "reportType";
  public static final String COMBINED = "combined";
  public static final String DATA = "data";

  public static final String CONFIGURATION = SingleReportDataDto.Fields.configuration;
  public static final String XML = SingleReportConfigurationDto.Fields.xml;
  public static final String AGGREGATION_TYPES = SingleReportConfigurationDto.Fields.aggregationTypes;

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
     XContentBuilder newBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIED)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(CREATED)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(OWNER)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIER)
        .field("type", "keyword")
      .endObject()
      .startObject(COLLECTION_ID)
       .field("type", "keyword")
      .endObject()
      .startObject(REPORT_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(COMBINED)
       .field("type", "boolean")
      .endObject();
     // @formatter:on
    newBuilder = addDataField(newBuilder);
    return newBuilder;
  }

  protected abstract XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException;

}
