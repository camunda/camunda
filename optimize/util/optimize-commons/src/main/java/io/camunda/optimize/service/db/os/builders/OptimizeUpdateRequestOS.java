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
import java.util.function.Function;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.util.ObjectBuilder;

public class OptimizeUpdateRequestOS<T, R> extends UpdateRequest.Builder<T, R> {

  public static <T, R> UpdateRequest<T, R> of(
      final Function<OptimizeUpdateRequestOS<T, R>, ObjectBuilder<UpdateRequest<T, R>>> fn) {
    return fn.apply(new OptimizeUpdateRequestOS<>()).build();
  }

  public UpdateRequest.Builder<T, R> optimizeIndex(
      final OptimizeOpenSearchClient client, final String... values) {
    return index(DatabaseClient.convertToPrefixedAliasName(values[0], client));
  }
}
