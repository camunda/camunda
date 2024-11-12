/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ProcessInstanceVariableFilter(String name, List<String> values)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ProcessInstanceVariableFilter> {

    private String name;
    private List<String> values;

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder values(final List<String> values) {
      this.values = addValuesToList(this.values, values);
      return this;
    }

    public Builder values(final String... values) {
      return values(collectValuesAsList(values));
    }

    @Override
    public ProcessInstanceVariableFilter build() {
      return new ProcessInstanceVariableFilter(
          name, Objects.requireNonNullElse(values, Collections.emptyList()));
    }
  }
}
