/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.aggregation;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AggregationPaginated;
import io.camunda.search.sort.ProcessDefinitionInstanceStatisticsSort;

public record ProcessDefinitionInstanceStatisticsAggregation(
    ProcessInstanceFilter filter,
    ProcessDefinitionInstanceStatisticsSort sort,
    SearchQueryPage page)
    implements AggregationBase, AggregationPaginated {

  public static final int AGGREGATION_TERMS_SIZE = 10000;
  public static final String AGGREGATION_NAME_BY_PROCESS_ID = "by_process_id";
  public static final String AGGREGATION_NAME_PAGE = "page";
  public static final String AGGREGATION_NAME_TOTAL_WITH_INCIDENT = "activeInstancesWithIncident";
  public static final String AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT =
      "activeInstancesWithoutIncident";
  public static final String AGGREGATION_NAME_LATEST_PROCESS_DEFINITION = "latestProcessDefinition";
  public static final String AGGREGATION_NAME_VERSION_COUNT = "versionCount";
  public static final String AGGREGATION_FIELD_KEY = "_key";
  public static final String AGGREGATION_FIELD_PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String AGGREGATION_NAME_PROCESS_DEFINITION_KEY_CARDINALITY =
      "processDefinitionKeyCardinality";
}
