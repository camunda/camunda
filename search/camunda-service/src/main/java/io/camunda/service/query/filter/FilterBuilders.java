/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class FilterBuilders {

  private FilterBuilders() {}

  public static ProcessInstanceFilter.Builder processInstance() {
    return new ProcessInstanceFilter.Builder();
  }

  public static ProcessInstanceFilter processInstance(
      final Function<ProcessInstanceFilter.Builder, DataStoreObjectBuilder<ProcessInstanceFilter>>
          fn) {
    return fn.apply(processInstance()).build();
  }

  public static VariableValueFilter.Builder variable() {
    return new VariableValueFilter.Builder();
  }

  public static VariableValueFilter variable(
      final Function<VariableValueFilter.Builder, DataStoreObjectBuilder<VariableValueFilter>> fn) {
    return fn.apply(variable()).build();
  }
}
