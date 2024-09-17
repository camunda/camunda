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

public class LicenseIndexV3 extends DefaultIndexMappingCreator<IndexSettings.Builder> {

  @Override
  public String getIndexName() {
    return "license";
  }

  @Override
  public int getVersion() {
    return 3;
  }

  @Override
  public TypeMapping.Builder addProperties(TypeMapping.Builder builder) {
    // @formatter:off
    return builder.properties("license", Property.of(p -> p.text(t -> t.index(false))));
    // @formatter:on
  }

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder builder) throws IOException {
    return builder.numberOfShards(Integer.toString(value));
  }
}
