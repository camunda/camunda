/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.builders;

import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.util.ObjectBuilder;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import java.util.function.Function;

public class OptimizeUpdateRequestBuilderES<T, R> extends UpdateRequest.Builder<T, R> {

  public static <T, R> UpdateRequest<T, R> of(
      final Function<OptimizeUpdateRequestBuilderES<T, R>, ObjectBuilder<UpdateRequest<T, R>>> fn) {
    return fn.apply(new OptimizeUpdateRequestBuilderES<>()).build();
  }

  public UpdateRequest.Builder<T, R> optimizeIndex(
      final OptimizeElasticsearchClient client, final String... values) {
    return index(DatabaseClient.convertToPrefixedAliasName(values[0], client));
  }
}
