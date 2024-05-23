/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core;

import io.camunda.data.clients.query.DataStoreQueryBuilders;
import io.camunda.data.clients.types.DataStoreSortOptionsBuilders;
import io.camunda.data.clients.util.DataStoreQueryBuildersDelegate;
import io.camunda.data.clients.util.DataStoreRequestBuildersDelegate;
import io.camunda.data.clients.util.DataStoreSortOptionsBuildersDelegate;

public abstract class DataStoreClientBase {

  protected DataStoreClientBase(
      final DataStoreQueryBuildersDelegate buildersDelegate,
      final DataStoreRequestBuildersDelegate requestBuilders,
      final DataStoreSortOptionsBuildersDelegate sortOptionsBuilders) {
    DataStoreQueryBuilders.setQueryBuilders(buildersDelegate);
    DataStoreRequestBuilders.setRequestBuilders(requestBuilders);
    DataStoreSortOptionsBuilders.setSortOptionsBuilders(sortOptionsBuilders);
  }
}
