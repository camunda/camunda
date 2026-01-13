/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.aggregation;

import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AggregationPaginated;
import io.camunda.search.sort.DecisionDefinitionSort;

/**
 * Aggregation used to retrieve only the latest version per (tenantId, decisionDefinitionId).
 *
 * <p>See {@link io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation} for the
 * reference implementation.
 */
public record DecisionDefinitionLatestVersionAggregation(
    DecisionDefinitionFilter filter, DecisionDefinitionSort sort, SearchQueryPage page)
    implements AggregationBase, AggregationPaginated {

  public static final int AGGREGATION_TERMS_SIZE = 10000;

  // Aggregation names
  public static final String AGGREGATION_NAME_BY_DECISION_ID = "by-decision-id";
  public static final String AGGREGATION_SOURCE_NAME_DECISION_ID = "decisionId";
  public static final String AGGREGATION_SOURCE_NAME_TENANT_ID = "tenantId";
  public static final String AGGREGATION_NAME_LATEST_DEFINITION = "latest_definition";

  // Aggregation fields
  public static final String AGGREGATION_GROUP_DECISION_ID = "decisionId";
  public static final String AGGREGATION_GROUP_TENANT_ID = "tenantId";
}
