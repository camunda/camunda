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

public interface DataStoreSortOptions {

  DataStoreFieldSort field();

  public static DataStoreSortOptions of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreSortOptions>> fn) {
    return DataStoreSortOptionsBuilders.sort(fn);
  }

  public interface Builder extends DataStoreObjectBuilder<DataStoreSortOptions> {

    Builder field(final DataStoreFieldSort sort);

    Builder field(
        final Function<DataStoreFieldSort.Builder, DataStoreObjectBuilder<DataStoreFieldSort>> fn);
  }
}
