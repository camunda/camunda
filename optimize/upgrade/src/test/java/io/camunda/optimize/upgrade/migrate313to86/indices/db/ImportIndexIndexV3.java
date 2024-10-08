/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices.db;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class ImportIndexIndexV3<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

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
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {

    return builder
        .properties(ENGINE, p -> p.keyword(k -> k))
        .properties(IMPORT_INDEX, p -> p.long_(k -> k));
  }
}
