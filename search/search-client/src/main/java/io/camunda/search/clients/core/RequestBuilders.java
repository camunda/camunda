/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class RequestBuilders {

  private RequestBuilders() {}

  public static SearchQueryRequest.Builder searchRequest() {
    return new SearchQueryRequest.Builder();
  }

  public static SearchQueryRequest searchRequest(
      final Function<SearchQueryRequest.Builder, ObjectBuilder<SearchQueryRequest>> fn) {
    return fn.apply(searchRequest()).build();
  }
}
