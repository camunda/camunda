/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.DatabaseConstants.ORDINAL_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

/**
 * Index that stores per-ordinal tick documents. Each document maps an ordinal value (a short
 * counter incremented every minute by the Zeebe engine) to a timestamp and a human-readable
 * date-time.
 */
public abstract class OrdinalIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;
  public static final String ORDINAL = "ordinal";
  public static final String TIMESTAMP_MS = "timestampMs";
  public static final String DATE_TIME = "dateTime";

  @Override
  public String getIndexName() {
    return ORDINAL_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ORDINAL, p -> p.integer(k -> k))
        .properties(TIMESTAMP_MS, p -> p.long_(k -> k))
        .properties(DATE_TIME, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)));
  }
}
