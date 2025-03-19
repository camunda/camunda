/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;

public record ProcessDefinitionFlowNodeStatisticsQuery(ProcessDefinitionStatisticsFilter filter)
    implements TypedSearchAggregationQuery<ProcessDefinitionStatisticsFilter> {

  public static final int AGGREGATION_TERMS_SIZE = 10000;
  public static final String AGGREGATION_TO_FLOW_NODES = "to-flow-nodes";
  public static final String AGGREGATION_FILTER_FLOW_NODES = "filter-flow-nodes";
  public static final String AGGREGATION_GROUP_FLOW_NODES = "group-flow-nodes";
  public static final String AGGREGATION_GROUP_FILTERS = "group-filters";
  public static final String AGGREGATION_ACTIVE = "active";
  public static final String AGGREGATION_COMPLETED = "completed";
  public static final String AGGREGATION_CANCELED = "canceled";
  public static final String AGGREGATION_INCIDENTS = "incidents";
}
