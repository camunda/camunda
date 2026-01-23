/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.aggregation;

import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AggregationPaginated;
import io.camunda.search.sort.IncidentProcessInstanceStatisticsByErrorSort;

public record IncidentProcessInstanceStatisticsByErrorAggregation(
    IncidentFilter filter, IncidentProcessInstanceStatisticsByErrorSort sort, SearchQueryPage page)
    implements AggregationBase, AggregationPaginated {

  public static final int AGGREGATION_TERMS_SIZE = 10_000;
  public static final String AGGREGATION_NAME_BY_ERROR = "by_error";
  public static final String AGGREGATION_NAME_AFFECTED_INSTANCES = "affected_instances";
  public static final String AGGREGATION_NAME_SORT_AND_PAGE = "sort_and_page";
  public static final String AGGREGATION_NAME_TOTAL_ESTIMATE = "total_estimate";
  public static final String AGGREGATION_NAME_ERROR_HASH = "error_hash";
  public static final String AGGREGATION_SCRIPT_LANG = "painless";

  // Buckets by error message
  public static final String AGGREGATION_ERROR_MESSAGE_SCRIPT =
      """
      def msg = params._source['errorMessage'];
      return msg == null ? '__NULL__' : msg;
      """;

  // Used only to estimate total unique (hash + message) combinations.
  public static final String AGGREGATION_TOTAL_ESTIMATE_SCRIPT =
      """
      def msg = params._source['errorMessage'];
      def hash = params._source['errorMessageHash'];
      return hash + '::' + (msg == null ? '__NULL__' : msg);
      """;

  public static final String SORT_FIELD_ACTIVE_INSTANCES_WITH_ERROR_COUNT =
      "activeInstancesWithErrorCount";

  public static String toBucketSortField(final String domainField) {
    return switch (domainField) {
      case SORT_FIELD_ACTIVE_INSTANCES_WITH_ERROR_COUNT -> AGGREGATION_NAME_AFFECTED_INSTANCES;
      case "errorMessage" -> "_key";
      default -> domainField;
    };
  }
}
