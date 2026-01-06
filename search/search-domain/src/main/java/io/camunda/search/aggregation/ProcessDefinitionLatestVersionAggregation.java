/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.aggregation;

import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AggregationPaginated;
import io.camunda.search.sort.ProcessDefinitionSort;

public record ProcessDefinitionLatestVersionAggregation(
    ProcessDefinitionFilter filter, ProcessDefinitionSort sort, SearchQueryPage page)
    implements AggregationBase, AggregationPaginated {
  public static final int AGGREGATION_TERMS_SIZE = 10000;

  // Aggregation names
  public static final String AGGREGATION_NAME_BY_PROCESS_ID = "by-process-id";
  public static final String AGGREGATION_SOURCE_NAME_BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String AGGREGATION_SOURCE_NAME_TENANT_ID = "tenantId";
  public static final String AGGREGATION_NAME_LATEST_DEFINITION = "latest_definition";

  // Aggregation fields
  public static final String AGGREGATION_GROUP_BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String AGGREGATION_GROUP_TENANT_ID = "tenantId";
  public static final String AGGREGATION_MAX_VERSION = "version";
}
