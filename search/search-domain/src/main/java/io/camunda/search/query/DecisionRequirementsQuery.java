/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.result.DecisionRequirementsQueryResultConfig;
import io.camunda.search.result.QueryResultConfigBuilders;
import io.camunda.search.sort.DecisionRequirementsSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record DecisionRequirementsQuery(
    DecisionRequirementsFilter filter,
    DecisionRequirementsSort sort,
    SearchQueryPage page,
    DecisionRequirementsQueryResultConfig resultConfig)
    implements TypedSearchQuery<DecisionRequirementsFilter, DecisionRequirementsSort> {

  public static DecisionRequirementsQuery of(
      final Function<Builder, ObjectBuilder<DecisionRequirementsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          DecisionRequirementsQuery,
          Builder,
          DecisionRequirementsFilter,
          DecisionRequirementsSort> {

    private static final DecisionRequirementsFilter EMPTY_FILTER =
        FilterBuilders.decisionRequirements().build();
    private static final DecisionRequirementsSort EMPTY_SORT =
        SortOptionBuilders.decisionRequirements().build();
    private static final DecisionRequirementsQueryResultConfig EXCLUDE_XML_RESULT_CONFIG =
        QueryResultConfigBuilders.decisionRequirements(r -> r.xml().exclude());

    private DecisionRequirementsFilter filter;
    private DecisionRequirementsSort sort;
    private DecisionRequirementsQueryResultConfig resultConfig;

    @Override
    public Builder filter(final DecisionRequirementsFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final DecisionRequirementsSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<
                DecisionRequirementsFilter.Builder, ObjectBuilder<DecisionRequirementsFilter>>
            fn) {
      return filter(FilterBuilders.decisionRequirements(fn));
    }

    public Builder sort(
        final Function<DecisionRequirementsSort.Builder, ObjectBuilder<DecisionRequirementsSort>>
            fn) {
      return sort(SortOptionBuilders.decisionRequirements(fn));
    }

    public Builder resultConfig(final DecisionRequirementsQueryResultConfig value) {
      resultConfig = value;
      return this;
    }

    public Builder resultConfig(
        final Function<
                DecisionRequirementsQueryResultConfig.Builder,
                ObjectBuilder<DecisionRequirementsQueryResultConfig>>
            fn) {
      return resultConfig(QueryResultConfigBuilders.decisionRequirements(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public DecisionRequirementsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      resultConfig = Objects.requireNonNullElse(resultConfig, EXCLUDE_XML_RESULT_CONFIG);
      return new DecisionRequirementsQuery(filter, sort, page(), resultConfig);
    }
  }
}
