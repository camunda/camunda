/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.builders;

import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.util.ObjectBuilder;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import java.util.function.Function;

public class OptimizeDeleteOperationBuilderES extends DeleteOperation.Builder {

  public static DeleteOperation of(
      final Function<OptimizeDeleteOperationBuilderES, ObjectBuilder<DeleteOperation>> fn) {
    return fn.apply(new OptimizeDeleteOperationBuilderES()).build();
  }

  public DeleteOperation.Builder optimizeIndex(
      final OptimizeElasticsearchClient client, final String index) {
    return index(DatabaseClient.convertToPrefixedAliasName(index, client));
  }
}
