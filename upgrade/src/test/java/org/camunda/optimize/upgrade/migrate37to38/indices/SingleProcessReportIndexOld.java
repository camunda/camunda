/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate37to38.indices;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class SingleProcessReportIndexOld extends AbstractReportIndex {

  public static final int VERSION = 8;

  @Override
  public String getIndexName() {
    return SINGLE_PROCESS_REPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  protected XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder.
      startObject(DATA)
        .field("type", "object")
        .field("dynamic", true)
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
          .startObject(CONFIGURATION)
            .field("type", "object")
            .field("dynamic", true)
            .startObject("properties")
              .startObject(XML)
                .field("type", "text")
                .field("index", true)
                .field("analyzer", "is_present_analyzer")
              .endObject()
            .endObject()
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }
}
