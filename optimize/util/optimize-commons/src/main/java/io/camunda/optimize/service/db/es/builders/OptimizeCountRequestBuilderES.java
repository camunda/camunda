/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.builders;

import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.util.ObjectBuilder;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import java.util.function.Function;

public class OptimizeCountRequestBuilderES extends CountRequest.Builder {

  public static CountRequest of(
      final Function<OptimizeCountRequestBuilderES, ObjectBuilder<CountRequest>> fn) {
    return fn.apply(new OptimizeCountRequestBuilderES()).build();
  }

  public CountRequest.Builder optimizeIndex(
      final OptimizeElasticsearchClient client, final String index) {
    return index(DatabaseClient.convertToPrefixedAliasName(index, client));
  }
}
