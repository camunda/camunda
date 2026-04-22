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

/**
 * Index mapping for {@code optimize-all-variables}. One document per Zeebe variable record (keyed
 * by variable key), imported from all {@code CREATED} and {@code UPDATED} variable intents
 * regardless of name. Used to evaluate performance impact of importing all variables into a
 * separate index.
 */
public abstract class AllVariablesIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String VARIABLE_KEY = "variableKey";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String SCOPE_KEY = "scopeKey";
  public static final String TENANT_ID = "tenantId";
  public static final String NAME = "name";
  public static final String VALUE = "value";
  public static final String TIMESTAMP = "timestamp";
  public static final String PARTITION_ID = "partitionId";
  public static final String SEQUENCE = "sequence";

  @Override
  public String getIndexName() {
    return DatabaseConstants.ALL_VARIABLES_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(VARIABLE_KEY, p -> p.keyword(k -> k))
        .properties(PROCESS_INSTANCE_KEY, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(SCOPE_KEY, p -> p.keyword(k -> k))
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        .properties(NAME, p -> p.keyword(k -> k))
        // value stored but not analyzed — avoids type conflicts across variable types
        .properties(VALUE, p -> p.keyword(k -> k.index(false).docValues(false)))
        .properties(TIMESTAMP, p -> p.date(d -> d.format("epoch_millis")))
        .properties(PARTITION_ID, p -> p.integer(i -> i))
        .properties(SEQUENCE, p -> p.long_(l -> l));
  }
}