/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import java.util.Locale;

/**
 * A flattened process instance index that stores ProcessInstanceDto without the nested collections
 * (flowNodeInstances, variables, and incidents). This index is designed for efficient aggregations
 * on pre-flattened data in a multi-stage pipeline approach.
 */
public abstract class FlatProcessInstanceIndex<TBuilder> extends AbstractInstanceIndex<TBuilder> {

  public static final int VERSION = 1;
  public static final String FLAT_PROCESS_INSTANCE_INDEX_PREFIX = "flat-process-instance-";

  public static final String START_DATE = ProcessInstanceDto.Fields.startDate;
  public static final String END_DATE = ProcessInstanceDto.Fields.endDate;
  public static final String DURATION = ProcessInstanceDto.Fields.duration;
  public static final String PROCESS_DEFINITION_KEY =
      ProcessInstanceDto.Fields.processDefinitionKey;
  public static final String PROCESS_DEFINITION_VERSION =
      ProcessInstanceDto.Fields.processDefinitionVersion;
  public static final String PROCESS_DEFINITION_ID = ProcessInstanceDto.Fields.processDefinitionId;
  public static final String PROCESS_INSTANCE_ID = ProcessInstanceDto.Fields.processInstanceId;
  public static final String BUSINESS_KEY = ProcessInstanceDto.Fields.businessKey;
  public static final String STATE = ProcessInstanceDto.Fields.state;
  public static final String DATA_SOURCE = ProcessInstanceDto.Fields.dataSource;
  public static final String TENANT_ID = ProcessInstanceDto.Fields.tenantId;
  public static final String PARTITION = ProcessInstanceDto.Fields.partition;

  private final String indexName;

  protected FlatProcessInstanceIndex(final String processInstanceIndexKey) {
    super(processInstanceIndexKey);
    indexName = getIndexPrefix() + processInstanceIndexKey.toLowerCase(Locale.ENGLISH);
  }

  public static String constructIndexName(final String processInstanceIndexKey) {
    return FLAT_PROCESS_INSTANCE_INDEX_PREFIX + processInstanceIndexKey.toLowerCase(Locale.ENGLISH);
  }

  /**
   * Constructs the index name using both the process definition key and the ordinal tick string
   * (e.g. {@code "20260306-1430"}). The ordinal tick is mandatory.
   */
  public static String constructIndexName(
      final String processInstanceIndexKey, final String ordinalTick) {
    return FLAT_PROCESS_INSTANCE_INDEX_PREFIX
        + processInstanceIndexKey.toLowerCase(Locale.ENGLISH)
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
    return TENANT_ID;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    // Only include top-level process instance fields, excluding the nested collections
    // (flowNodeInstances, variables, incidents)
    return builder
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_VERSION, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_INSTANCE_ID, p -> p.keyword(k -> k))
        .properties(BUSINESS_KEY, p -> p.keyword(k -> k))
        .properties(START_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(END_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(DURATION, p -> p.long_(k -> k))
        .properties(DATA_SOURCE, p -> p.object(k -> k.dynamic(DynamicMapping.True)))
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        .properties(STATE, p -> p.keyword(k -> k))
        .properties(PARTITION, p -> p.integer(k -> k));
  }

  protected String getIndexPrefix() {
    return FLAT_PROCESS_INSTANCE_INDEX_PREFIX;
  }
}
