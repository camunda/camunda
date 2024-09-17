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

public class EventTraceStateIndexV2 extends DefaultIndexMappingCreator<IndexSettings.Builder> {

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder builder) throws IOException {
    return builder.numberOfShards(Integer.toString(value));
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    // only include sample field since this index is used for deletion IT
    // @formatter:off
    return builder.properties("ID", Property.of(p -> p.keyword(t -> t)));
    // @formatter:on
  }

  @Override
  public String getIndexName() {
    return "event-trace-state-external";
  }

  @Override
  public int getVersion() {
    return 2;
  }
}
