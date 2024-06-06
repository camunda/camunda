/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class SearchQueryBuilders {

  private SearchQueryBuilders() {}

  public static ProcessInstanceQuery.Builder processInstanceSearchQuery() {
    return new ProcessInstanceQuery.Builder();
  }

  public static ProcessInstanceQuery processInstanceSearchQuery(
      final Function<ProcessInstanceQuery.Builder, ObjectBuilder<ProcessInstanceQuery>> fn) {
    return fn.apply(processInstanceSearchQuery()).build();
  }
}
