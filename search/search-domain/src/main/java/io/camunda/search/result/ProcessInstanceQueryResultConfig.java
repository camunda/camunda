/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.result;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record ProcessInstanceQueryResultConfig(List<FieldFilter> fieldFilters)
    implements QueryResultConfig {

  @Override
  public List<FieldFilter> getFieldFilters() {
    return fieldFilters;
  }

  public static ProcessInstanceQueryResultConfig of(
      final Function<Builder, ObjectBuilder<ProcessInstanceQueryResultConfig>> fn) {
    return QueryResultConfigBuilders.processInstance(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<ProcessInstanceQueryResultConfig> {

    public Builder key() {
      currentFieldFilter = new FieldFilter("key", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcessInstanceQueryResultConfig build() {
      return new ProcessInstanceQueryResultConfig(fieldFilters);
    }
  }
}
