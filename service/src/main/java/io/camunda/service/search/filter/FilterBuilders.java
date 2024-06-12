/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class FilterBuilders {

  private FilterBuilders() {}

  public static ProcessInstanceFilter.Builder processInstance() {
    return new ProcessInstanceFilter.Builder();
  }

  public static ProcessInstanceFilter processInstance(
      final Function<ProcessInstanceFilter.Builder, ObjectBuilder<ProcessInstanceFilter>> fn) {
    return fn.apply(processInstance()).build();
  }

  public static VariableValueFilter.Builder variableValue() {
    return new VariableValueFilter.Builder();
  }

  public static VariableValueFilter variableValue(
      final Function<VariableValueFilter.Builder, ObjectBuilder<VariableValueFilter>> fn) {
    return fn.apply(variableValue()).build();
  }

  public static DateValueFilter.Builder dateValue() {
    return new DateValueFilter.Builder();
  }

  public static DateValueFilter dateValue(
      final Function<DateValueFilter.Builder, ObjectBuilder<DateValueFilter>> fn) {
    return fn.apply(dateValue()).build();
  }
}
