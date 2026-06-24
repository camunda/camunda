/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class ReportShareIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 3;

  public static final String ID = "id";
  public static final String REPORT_ID = "reportId";
  public static final String POSITION = "position";
  public static final String X_POSITION = "x";
  public static final String Y_POSITION = "y";

  @Override
  public String getIndexName() {
    return DatabaseConstants.REPORT_SHARE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(
            POSITION,
            p ->
                p.nested(
                    n ->
                        n.properties(X_POSITION, p2 -> p2.keyword(k -> k))
                            .properties(Y_POSITION, p2 -> p2.keyword(k -> k))))
        .properties(REPORT_ID, p -> p.keyword(k -> k));
  }
}
