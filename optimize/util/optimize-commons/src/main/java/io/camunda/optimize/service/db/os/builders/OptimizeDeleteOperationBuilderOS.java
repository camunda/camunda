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
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.util.ObjectBuilder;

public class OptimizeDeleteOperationBuilderOS extends DeleteOperation.Builder {

  public static DeleteOperation of(
      final Function<OptimizeDeleteOperationBuilderOS, ObjectBuilder<DeleteOperation>> fn) {
    return fn.apply(new OptimizeDeleteOperationBuilderOS()).build();
  }

  public DeleteOperation.Builder optimizeIndex(
      final OptimizeOpenSearchClient client, final String index) {
    return index(DatabaseClient.convertToPrefixedAliasName(index, client));
  }
}
