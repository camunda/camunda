/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.aggregation;

import io.camunda.search.filter.UsageMetricsTUFilter;

public record UsageMetricsTUAggregation(UsageMetricsTUFilter filter) implements AggregationBase {

  public static final int AGGREGATION_TERMS_SIZE = 20000;
  public static final String AGGREGATION_TERMS_TENANT_ID = "termsTenantId";
  public static final String AGGREGATION_TERMS_ASSIGNEE_HASH = "termsAssigneeHash";
}
