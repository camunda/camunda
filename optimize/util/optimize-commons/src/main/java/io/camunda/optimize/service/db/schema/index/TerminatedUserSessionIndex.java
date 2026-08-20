/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class TerminatedUserSessionIndex<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 3;

  public static final String ID = "id";
  public static final String TERMINATION_TIMESTAMP = "terminationTimestamp";

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {

    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(TERMINATION_TIMESTAMP, p -> p.date(d -> d.format(OPTIMIZE_DATE_FORMAT)));
  }

  @Override
  public String getIndexName() {
    return DatabaseConstants.TERMINATED_USER_SESSION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
