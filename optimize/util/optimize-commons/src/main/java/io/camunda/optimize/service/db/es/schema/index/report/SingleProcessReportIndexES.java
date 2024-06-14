/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.schema.index.report;

import io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class SingleProcessReportIndexES extends SingleProcessReportIndex<XContentBuilder> {

  @Override
  public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder)
      throws IOException {
    return contentBuilder.field(key, value);
  }
}
