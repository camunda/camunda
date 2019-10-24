/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.report;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;

@Component
public class CombinedReportIndex extends AbstractReportIndex {

  public static final int VERSION = 3;

  public static final String VISUALIZATION = "visualization";
  public static final String CONFIGURATION = "configuration";

  public static final String REPORTS = "reports";
  public static final String REPORT_ITEM_ID = "id";
  public static final String REPORT_ITEM_COLOR = "color";

  @Override
  public String getIndexName() {
    return COMBINED_REPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  protected XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder.
      startObject(DATA)
        .field("type", "nested")
        .startObject("properties")
          .startObject(CONFIGURATION)
            .field("enabled", false)
          .endObject()
          .startObject(VISUALIZATION)
            .field("type", "keyword")
          .endObject()
          .startObject(REPORTS)
            .field("type", "nested")
            .startObject("properties")
              .startObject(REPORT_ITEM_ID)
                .field("type", "keyword")
              .endObject()
              .startObject(REPORT_ITEM_COLOR)
                .field("type", "keyword")
              .endObject()
            .endObject()
          .endObject()
        .endObject()
      .endObject();
  }
}
