/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public interface DataStoreMatchAllQuery extends DataStoreQueryVariant {

  static DataStoreMatchAllQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreMatchAllQuery>> fn) {
    return DataStoreQueryBuilders.matchAll(fn);
  }

  public interface Builder extends DataStoreObjectBuilder<DataStoreMatchAllQuery> {}
}
