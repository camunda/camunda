/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;

public class SettingsIndexV2 extends DefaultIndexMappingCreator<IndexSettings.Builder> {

  public static final int VERSION = 2;

  public static final String SHARING_ENABLED = SettingsDto.Fields.sharingEnabled.name();
  public static final String LAST_MODIFIED = SettingsDto.Fields.lastModified.name();

  @Override
  public String getIndexName() {
    return DatabaseConstants.SETTINGS_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties("metadataTelemetryEnabled", p -> p.boolean_(b -> b))
        .properties(SHARING_ENABLED, p -> p.boolean_(b -> b))
        .properties("lastModifier", p -> p.keyword(b -> b))
        .properties(LAST_MODIFIED, p -> p.date(b -> b.format(OPTIMIZE_DATE_FORMAT)));
  }

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder builder) throws IOException {
    return builder.numberOfShards(Integer.toString(value));
  }
}
