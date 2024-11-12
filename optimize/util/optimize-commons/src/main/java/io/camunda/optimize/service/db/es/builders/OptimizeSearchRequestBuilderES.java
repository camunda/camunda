/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.builders;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.util.ObjectBuilder;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import java.util.List;
import java.util.function.Function;

public class OptimizeSearchRequestBuilderES extends SearchRequest.Builder {

  public static SearchRequest of(
      final Function<OptimizeSearchRequestBuilderES, ObjectBuilder<SearchRequest>> fn) {
    return fn.apply(new OptimizeSearchRequestBuilderES()).build();
  }

  public SearchRequest.Builder optimizeIndex(
      final OptimizeElasticsearchClient databaseClient, final String... values) {
    return index(List.of(DatabaseClient.convertToPrefixedAliasNames(values, databaseClient)));
  }
}
