/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.aggregation;

import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AggregationPaginated;

public record AuditLogLatestSuccessfulAggregation(AuditLogFilter filter, SearchQueryPage page)
    implements AggregationBase, AggregationPaginated {

  public static final String AGGREGATION_NAME_BY_ENTITY_KEY = "by-entity-key";
  public static final String AGGREGATION_SOURCE_NAME_ENTITY_KEY = "entityKey";
  public static final String AGGREGATION_NAME_LATEST_AUDIT_LOG = "latest-audit-log";
}
