/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DYNAMIC_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROPERTIES_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_BOOLEAN;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_OBJECT;

public class ProcessOverviewIndex extends DefaultIndexMappingCreator {
  public static final int VERSION = 2;

  public static final String PROCESS_DEFINITION_KEY = ProcessOverviewDto.Fields.processDefinitionKey;
  public static final String OWNER = ProcessOverviewDto.Fields.owner;
  public static final String DIGEST = ProcessOverviewDto.Fields.digest;
  public static final String LAST_KPI_EVALUATION = ProcessOverviewDto.Fields.lastKpiEvaluationResults;
  public static final String ENABLED = ProcessDigestResponseDto.Fields.enabled;
  public static final String KPI_REPORT_RESULTS = ProcessDigestDto.Fields.kpiReportResults;

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
      .startObject(LAST_KPI_EVALUATION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
        .field(DYNAMIC_PROPERTY_TYPE, true)
      .endObject()
      .startObject(DIGEST)
        .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
        .startObject(PROPERTIES_PROPERTY_TYPE)
          .startObject(ENABLED)
            .field(MAPPING_PROPERTY_TYPE, TYPE_BOOLEAN)
          .endObject()
          .startObject(KPI_REPORT_RESULTS)
            .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
            .field(DYNAMIC_PROPERTY_TYPE, true)
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }
}
