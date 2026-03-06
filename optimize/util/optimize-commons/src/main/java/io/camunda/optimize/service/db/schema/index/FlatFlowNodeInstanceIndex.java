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
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import java.util.Locale;

/**
 * A flattened flow node instance index that stores FlowNodeInstanceDto with required process
 * definition fields. This index is designed for efficient aggregations in a multi-stage pipeline.
 */
public abstract class FlatFlowNodeInstanceIndex<TBuilder> extends AbstractInstanceIndex<TBuilder> {

  public static final int VERSION = 1;
  public static final String FLAT_FLOW_NODE_INSTANCE_INDEX_PREFIX = "flat-flow-node-instance-";

  // Required process fields
  public static final String PROCESS_DEFINITION_KEY =
      ProcessInstanceDto.Fields.processDefinitionKey;
  public static final String PROCESS_DEFINITION_VERSION =
      ProcessInstanceDto.Fields.processDefinitionVersion;
  public static final String PROCESS_DEFINITION_ID = ProcessInstanceDto.Fields.processDefinitionId;
  public static final String PROCESS_INSTANCE_ID = ProcessInstanceDto.Fields.processInstanceId;

  // FlowNodeInstance specific fields
  public static final String FLOW_NODE_INSTANCE_ID = FlowNodeInstanceDto.Fields.flowNodeInstanceId;
  public static final String FLOW_NODE_ID = FlowNodeInstanceDto.Fields.flowNodeId;
  public static final String FLOW_NODE_TYPE = FlowNodeInstanceDto.Fields.flowNodeType;
  public static final String TOTAL_DURATION_IN_MS = FlowNodeInstanceDto.Fields.totalDurationInMs;
  public static final String START_DATE = FlowNodeInstanceDto.Fields.startDate;
  public static final String END_DATE = FlowNodeInstanceDto.Fields.endDate;
  public static final String CANCELED = FlowNodeInstanceDto.Fields.canceled;
  public static final String DEFINITION_KEY = FlowNodeInstanceDto.Fields.definitionKey;
  public static final String DEFINITION_VERSION = FlowNodeInstanceDto.Fields.definitionVersion;
  public static final String TENANT_ID = FlowNodeInstanceDto.Fields.tenantId;

  // User task specific fields
  public static final String USER_TASK_INSTANCE_ID = FlowNodeInstanceDto.Fields.userTaskInstanceId;
  public static final String DUE_DATE = FlowNodeInstanceDto.Fields.dueDate;
  public static final String DELETE_REASON = FlowNodeInstanceDto.Fields.deleteReason;
  public static final String ASSIGNEE = FlowNodeInstanceDto.Fields.assignee;
  public static final String CANDIDATE_GROUPS = FlowNodeInstanceDto.Fields.candidateGroups;
  public static final String IDLE_DURATION_IN_MS = FlowNodeInstanceDto.Fields.idleDurationInMs;
  public static final String WORK_DURATION_IN_MS = FlowNodeInstanceDto.Fields.workDurationInMs;
  public static final String PARTITION = "partition";

  private final String indexName;

  protected FlatFlowNodeInstanceIndex(final String flowNodeInstanceIndexKey) {
    super(flowNodeInstanceIndexKey);
    indexName = getIndexPrefix() + flowNodeInstanceIndexKey.toLowerCase(Locale.ENGLISH);
  }

  public static String constructIndexName(final String flowNodeInstanceIndexKey) {
    return FLAT_FLOW_NODE_INSTANCE_INDEX_PREFIX
        + flowNodeInstanceIndexKey.toLowerCase(Locale.ENGLISH);
  }

  /**
   * Constructs the index name using both the process definition key and the ordinal tick string
   * (e.g. {@code "20260306-1430"}). The ordinal tick is mandatory.
   */
  public static String constructIndexName(
      final String flowNodeInstanceIndexKey, final String ordinalTick) {
    return FLAT_FLOW_NODE_INSTANCE_INDEX_PREFIX
        + flowNodeInstanceIndexKey.toLowerCase(Locale.ENGLISH)
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
    return builder
        // Required process definition fields
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_VERSION, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_INSTANCE_ID, p -> p.keyword(k -> k))
        // FlowNodeInstance specific fields
        .properties(FLOW_NODE_INSTANCE_ID, p -> p.keyword(k -> k))
        .properties(FLOW_NODE_ID, p -> p.keyword(k -> k))
        .properties(FLOW_NODE_TYPE, p -> p.keyword(k -> k))
        .properties(TOTAL_DURATION_IN_MS, p -> p.long_(k -> k))
        .properties(START_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(END_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(CANCELED, p -> p.boolean_(k -> k))
        .properties(DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(DEFINITION_VERSION, p -> p.keyword(k -> k))
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        // User task specific fields
        .properties(USER_TASK_INSTANCE_ID, p -> p.keyword(k -> k))
        .properties(DUE_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(DELETE_REASON, p -> p.keyword(k -> k))
        .properties(ASSIGNEE, p -> p.keyword(k -> k))
        .properties(CANDIDATE_GROUPS, p -> p.keyword(k -> k))
        .properties(IDLE_DURATION_IN_MS, p -> p.long_(k -> k))
        .properties(WORK_DURATION_IN_MS, p -> p.long_(k -> k))
        .properties(PARTITION, p -> p.integer(k -> k));
  }

  protected String getIndexPrefix() {
    return FLAT_FLOW_NODE_INSTANCE_INDEX_PREFIX;
  }
}
