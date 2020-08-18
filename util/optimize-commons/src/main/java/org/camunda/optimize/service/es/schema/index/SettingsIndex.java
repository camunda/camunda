/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.SettingsDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SETTINGS_INDEX_NAME;

public class SettingsIndex extends DefaultIndexMappingCreator {
  public static final int VERSION = 1;

  public static final String METADATA_TELEMETRY_ENABLED = SettingsDto.Fields.metadataTelemetryEnabled.name();

  @Override
  public String getIndexName() {
    return SETTINGS_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(METADATA_TELEMETRY_ENABLED)
        .field("type", "boolean")
      .endObject();
    // @formatter:on
  }
}
