/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.IGNORE_ABOVE_CHAR_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class JobRegistryIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String ID = "id";
  public static final String JOB_TYPE = "jobType";
  public static final String TARGET_ENTITY_TYPE = "targetEntityType";
  public static final String TARGET_ENTITY_ID = "targetEntityId";
  public static final String STATUS = "status";
  public static final String ERROR_MESSAGE = "errorMessage";
  public static final String CREATED_AT = "createdAt";
  public static final String UPDATED_AT = "updatedAt";

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(JOB_TYPE, p -> p.keyword(k -> k))
        .properties(TARGET_ENTITY_TYPE, p -> p.keyword(k -> k))
        .properties(TARGET_ENTITY_ID, p -> p.keyword(k -> k))
        .properties(STATUS, p -> p.keyword(k -> k))
        .properties(ERROR_MESSAGE, p -> p.keyword(k -> k.ignoreAbove(IGNORE_ABOVE_CHAR_LIMIT)))
        .properties(CREATED_AT, p -> p.date(d -> d.format(OPTIMIZE_DATE_FORMAT)))
        .properties(UPDATED_AT, p -> p.date(d -> d.format(OPTIMIZE_DATE_FORMAT)));
  }

  @Override
  public String getIndexName() {
    return DatabaseConstants.JOB_REGISTRY_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
