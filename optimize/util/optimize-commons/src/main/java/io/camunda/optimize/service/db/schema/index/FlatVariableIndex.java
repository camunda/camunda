/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.IGNORE_ABOVE_CHAR_LIMIT;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import java.util.Locale;

/**
 * A flattened variable index that stores SimpleProcessVariableDto with required process definition
 * fields. This index is designed for efficient aggregations in a multi-stage pipeline.
 */
public abstract class FlatVariableIndex<TBuilder> extends AbstractInstanceIndex<TBuilder> {

  public static final int VERSION = 1;
  public static final String FLAT_VARIABLE_INDEX_PREFIX = "flat-variable-";

  // Required process fields
  public static final String PROCESS_DEFINITION_KEY =
      ProcessInstanceDto.Fields.processDefinitionKey;
  public static final String PROCESS_DEFINITION_VERSION =
      ProcessInstanceDto.Fields.processDefinitionVersion;
  public static final String PROCESS_DEFINITION_ID = ProcessInstanceDto.Fields.processDefinitionId;
  public static final String PROCESS_INSTANCE_ID = ProcessInstanceDto.Fields.processInstanceId;

  // Variable specific fields
  public static final String VARIABLE_ID = SimpleProcessVariableDto.Fields.id;
  public static final String VARIABLE_NAME = SimpleProcessVariableDto.Fields.name;
  public static final String VARIABLE_TYPE = SimpleProcessVariableDto.Fields.type;
  public static final String VARIABLE_VALUE = SimpleProcessVariableDto.Fields.value;
  public static final String VARIABLE_VERSION = SimpleProcessVariableDto.Fields.version;
  public static final String PARTITION = "partition";

  private final String indexName;

  protected FlatVariableIndex(final String variableIndexKey) {
    super(variableIndexKey);
    indexName = getIndexPrefix() + variableIndexKey.toLowerCase(Locale.ENGLISH);
  }

  public static String constructIndexName(final String variableIndexKey) {
    return FLAT_VARIABLE_INDEX_PREFIX + variableIndexKey.toLowerCase(Locale.ENGLISH);
  }

  /**
   * Constructs the index name using both the process definition key and the ordinal tick string
   * (e.g. {@code "20260306-1430"}). The ordinal tick is mandatory.
   */
  public static String constructIndexName(final String variableIndexKey, final String ordinalTick) {
    return FLAT_VARIABLE_INDEX_PREFIX
        + variableIndexKey.toLowerCase(Locale.ENGLISH)
        + "-"
        + ordinalTick;
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
    return PROCESS_DEFINITION_VERSION;
  }

  @Override
  public String getTenantIdFieldName() {
    // Variables don't have a tenant field in the DTO, returning null
    return null;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        // Required process definition fields
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_VERSION, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_INSTANCE_ID, p -> p.keyword(k -> k))
        // Variable specific fields
        .properties(VARIABLE_ID, p -> p.keyword(k -> k))
        .properties(VARIABLE_NAME, p -> p.keyword(k -> k))
        .properties(VARIABLE_TYPE, p -> p.keyword(k -> k))
        .properties(
            VARIABLE_VALUE,
            p -> p.keyword(k -> addValueMultifields(k.ignoreAbove(IGNORE_ABOVE_CHAR_LIMIT))))
        .properties(VARIABLE_VERSION, p -> p.long_(k -> k))
        .properties(PARTITION, p -> p.integer(k -> k));
  }

  protected String getIndexPrefix() {
    return FLAT_VARIABLE_INDEX_PREFIX;
  }
}
