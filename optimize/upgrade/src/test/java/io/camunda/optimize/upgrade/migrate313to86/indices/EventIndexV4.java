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

public class EventIndexV4 extends DefaultIndexMappingCreator<IndexSettings.Builder> {

  public static final int VERSION = 4;

  @Override
  public String getIndexName() {
    return "event";
  }

  @Override
  public String getIndexNameInitialSuffix() {
    return "-000001";
  }

  @Override
  public boolean isCreateFromTemplate() {
    return true;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    // only include sample field since this index is used for deletion IT
    // @formatter:off
    return builder.properties("ID", Property.of(p -> p.keyword(t -> t)));
    // @formatter:on
  }

  @Override
  public IndexSettings.Builder addStaticSetting(
      String key, int value, IndexSettings.Builder builder) throws IOException {
    return builder.numberOfShards(Integer.toString(value));
  }
}
