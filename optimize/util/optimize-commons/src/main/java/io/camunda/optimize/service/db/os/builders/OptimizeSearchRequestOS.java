/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.builders;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import java.util.List;
import java.util.function.Function;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.util.ObjectBuilder;

public class OptimizeSearchRequestOS extends SearchRequest.Builder {

  public static SearchRequest of(
      final Function<OptimizeSearchRequestOS, ObjectBuilder<SearchRequest>> fn) {
    return fn.apply(new OptimizeSearchRequestOS()).build();
  }

  public SearchRequest.Builder optimizeIndex(
      final OptimizeOpenSearchClient databaseClient, final String... values) {
    return index(List.of(DatabaseClient.convertToPrefixedAliasNames(values, databaseClient)));
  }
}
