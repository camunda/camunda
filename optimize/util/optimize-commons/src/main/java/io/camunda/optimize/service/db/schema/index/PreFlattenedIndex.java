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
import java.util.Locale;

/**
 * Index that stores {@code PreFlattenedDTO} documents produced by the sliding window cache each
 * second. Each document represents the start of a process instance enriched with the value of the
 * {@code region} variable (if present in the 10-second window for the same process instance).
 */
public abstract class PreFlattenedIndex<TBuilder> extends AbstractInstanceIndex<TBuilder> {

  public static final int VERSION = 1;
  public static final String PRE_FLATTENED_INDEX_PREFIX = "pre-flattened-";

  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String TENANT = "tenant";
  public static final String PARTITION = "partition";
  public static final String START_TIME = "startTime";
  public static final String REGION = "region";

  private final String indexName;

  protected PreFlattenedIndex(final String indexKey) {
    super(indexKey);
    indexName = PRE_FLATTENED_INDEX_PREFIX + indexKey.toLowerCase(Locale.ENGLISH);
  }

  public static String constructIndexName(final String indexKey) {
    return PRE_FLATTENED_INDEX_PREFIX + indexKey.toLowerCase(Locale.ENGLISH);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public String getDefinitionKeyFieldName() {
    return PROCESS_DEFINITION_KEY;
  }

  @Override
  public String getDefinitionVersionFieldName() {
    // not used for this index
    return null;
  }

  @Override
  public String getTenantIdFieldName() {
    return TENANT;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(PROCESS_INSTANCE_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(TENANT, p -> p.keyword(k -> k))
        .properties(PARTITION, p -> p.integer(k -> k))
        .properties(START_TIME, p -> p.date(d -> d.format(OPTIMIZE_DATE_FORMAT)))
        .properties(REGION, p -> p.keyword(k -> k));
  }

  protected String getIndexPrefix() {
    return PRE_FLATTENED_INDEX_PREFIX;
  }
}
