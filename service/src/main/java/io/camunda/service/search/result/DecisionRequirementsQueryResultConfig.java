/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.result;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record DecisionRequirementsQueryResultConfig(List<FieldFilter> fieldFilters)
    implements QueryResultConfig {

  @Override
  public List<FieldFilter> getFieldFilters() {
    return fieldFilters;
  }

  public static DecisionRequirementsQueryResultConfig of(
      final Function<Builder, ObjectBuilder<DecisionRequirementsQueryResultConfig>> fn) {
    return QueryResultConfigBuilders.decisionRequirements(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<DecisionRequirementsQueryResultConfig> {

    public Builder xml() {
      currentFieldFilter = new FieldFilter("xml", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public DecisionRequirementsQueryResultConfig build() {
      return new DecisionRequirementsQueryResultConfig(fieldFilters);
    }
  }
}
