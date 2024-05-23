/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.types;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public interface DataStoreFieldSort {

  String field();

  SortOrder order();

  public static DataStoreFieldSort of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreFieldSort>> fn) {
    return DataStoreSortOptionsBuilders.field(fn);
  }

  public interface Builder extends DataStoreObjectBuilder<DataStoreFieldSort> {

    Builder field(final String field);

    Builder asc();

    Builder desc();

    Builder order(final SortOrder order);

    Builder missing(String value);
  }
}
