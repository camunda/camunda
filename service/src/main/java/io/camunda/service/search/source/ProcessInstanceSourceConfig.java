/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.source;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record ProcessInstanceSourceConfig(List<FieldFilter> fieldFilters) implements SourceConfig {

  @Override
  public List<FieldFilter> getFieldFilters() {
    return fieldFilters;
  }

  public static ProcessInstanceSourceConfig of(
      final Function<Builder, ObjectBuilder<ProcessInstanceSourceConfig>> fn) {
    return SourceConfigBuilders.processInstance(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<ProcessInstanceSourceConfig> {

    public Builder processInstanceKey() {
      currentFieldFilter = new FieldFilter("processInstanceKey", true);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcessInstanceSourceConfig build() {
      return new ProcessInstanceSourceConfig(fieldFilters);
    }
  }
}
