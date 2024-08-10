/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.page;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class SearchQueryPageBuilders {

  public static SearchQueryPage.Builder page() {
    return new SearchQueryPage.Builder();
  }

  public static SearchQueryPage page(
      final Function<SearchQueryPage.Builder, ObjectBuilder<SearchQueryPage>> fn) {
    return fn.apply(page()).build();
  }
}
