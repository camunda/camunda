/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.SORT_FIELD_SETTING;
import static io.camunda.optimize.service.db.DatabaseConstants.SORT_ORDER_SETTING;
import static io.camunda.optimize.service.db.DatabaseConstants.SORT_SETTING;

import io.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class ExternalProcessVariableIndexES extends ExternalProcessVariableIndex<XContentBuilder> {

  @Override
  public XContentBuilder getStaticSettings(
      XContentBuilder xContentBuilder, ConfigurationService configurationService)
      throws IOException {
    // @formatter:off
    final XContentBuilder newXContentBuilder =
        super.getStaticSettings(xContentBuilder, configurationService);
    return newXContentBuilder
        .startObject(SORT_SETTING)
        .field(SORT_FIELD_SETTING, INGESTION_TIMESTAMP)
        .field(SORT_ORDER_SETTING, "desc")
        .endObject();
    // @formatter:on
  }

  @Override
  public XContentBuilder addStaticSetting(
      final String key, final int value, final XContentBuilder contentBuilder) throws IOException {
    return contentBuilder.field(key, value);
  }
}
