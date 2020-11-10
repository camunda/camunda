/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate32To33.indices;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class SingleProcessReportIndexV4Old extends AbstractReportIndex {

  public static final int VERSION = 4;

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
            .field("enabled", false)
          .endObject()
          .startObject(ProcessReportDataDto.Fields.groupBy)
            .field("enabled", false)
          .endObject()
          .startObject(ProcessReportDataDto.Fields.filter)
            .field("enabled", false)
          .endObject()
          .startObject(SingleReportDataDto.Fields.configuration.name())
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
