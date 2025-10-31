/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.aggregation;

import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AggregationPaginated;

public record ProcessDefinitionMessageSubscriptionStatisticsAggregation(SearchQueryPage page)
    implements AggregationBase, AggregationPaginated {

  public static final int AGGREGATION_COMPOSITE_SIZE = 10000;

  // Aggregation names
  public static final String AGGREGATION_NAME_BY_PROCESS_DEF_KEY_AND_TENANT_ID =
      "byProcessDefKeyAndTenantId";
  public static final String AGGREGATION_NAME_TOP_HIT = "topHit";
  public static final String AGGREGATION_NAME_ACTIVE_SUBSCRIPTIONS = "activeSubscriptions";
  public static final String AGGREGATION_NAME_PROCESS_INSTANCES_WITH_ACTIVE_SUBSCRIPTIONS =
      "processInstancesWithActiveSubscriptions";
  public static final String AGGREGATION_SOURCE_NAME_PROCESS_DEFINITION_KEY =
      "processDefinitionKey";
  public static final String AGGREGATION_SOURCE_NAME_TENANT_ID = "tenantId";

  // Aggregation fields
  public static final String AGGREGATION_FIELD_BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String AGGREGATION_FIELD_PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String AGGREGATION_FIELD_FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";
  public static final String AGGREGATION_FIELD_PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String AGGREGATION_FIELD_TENANT_ID = "tenantId";
}
