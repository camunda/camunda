/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.util;

import io.camunda.data.clients.types.DataStoreFieldSort;
import io.camunda.data.clients.types.DataStoreSortOptions;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public interface DataStoreSortOptionsBuildersDelegate {

  DataStoreSortOptions.Builder sort();

  DataStoreSortOptions sort(
      final Function<DataStoreSortOptions.Builder, DataStoreObjectBuilder<DataStoreSortOptions>>
          fn);

  DataStoreFieldSort.Builder field();

  DataStoreFieldSort field(
      final Function<DataStoreFieldSort.Builder, DataStoreObjectBuilder<DataStoreFieldSort>> fn);
}
