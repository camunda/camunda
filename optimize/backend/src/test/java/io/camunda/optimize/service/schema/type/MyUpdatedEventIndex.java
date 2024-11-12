/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.schema.type;

import static io.camunda.optimize.service.db.schema.index.MetadataIndex.SCHEMA_VERSION;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.slf4j.Logger;

public abstract class MyUpdatedEventIndex<TBuilder> implements IndexMappingCreator<TBuilder> {

  public static final String MY_NEW_FIELD = "myAwesomeNewField";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MyUpdatedEventIndex.class);

  @Override
  public String getIndexName() {
    return DatabaseConstants.METADATA_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return 3;
  }

  @Override
  public TypeMapping getSource() {
    return TypeMapping.of(
        t ->
            t.properties(SCHEMA_VERSION, p -> p.keyword(k -> k))
                .properties(MY_NEW_FIELD, p -> p.keyword(k -> k)));
  }
}
