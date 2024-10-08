/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;

public class ImportIndexIndexV3 extends DefaultIndexMappingCreator<IndexSettings.Builder> {

  public static final int VERSION = 3;

  public static final String IMPORT_INDEX = "importIndex";
  public static final String ENGINE = "engine";

  @Override
  public String getIndexName() {
    return "import-index";
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public boolean isImportIndex() {
    return true;
  }

  @Override
  public TypeMapping.Builder addProperties(TypeMapping.Builder builder) {
    // @formatter:off
    return builder
        .properties(ENGINE, Property.of(p -> p.keyword(t -> t)))
        .properties(IMPORT_INDEX, Property.of(p -> p.long_(t -> t)));
    // @formatter:on
  }

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder builder) throws IOException {
    return builder.numberOfShards(Integer.toString(value));
  }
}
