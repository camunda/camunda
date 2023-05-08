/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index.report;

import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;

public class CombinedReportIndex extends AbstractReportIndex {

  public static final int VERSION = 5;

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
  protected XContentBuilder addReportTypeSpecificFields(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder.
      startObject(DATA)
        .field("type", "nested")
        .startObject("properties")
          .startObject(CONFIGURATION)
            .field(MAPPING_ENABLED_SETTING, false)
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
    // @formatter:on
  }
}
