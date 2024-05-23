/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core;

import io.camunda.data.clients.util.DataStoreRequestBuildersDelegate;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class DataStoreRequestBuilders {

  private static DataStoreRequestBuildersDelegate requestBuilders;

  private DataStoreRequestBuilders() {}

  public static void setRequestBuilders(final DataStoreRequestBuildersDelegate requestBuilders) {
    DataStoreRequestBuilders.requestBuilders = requestBuilders;
  }

  public static DataStoreSearchRequest.Builder searchRequest() {
    return requestBuilders.searchRequest();
  }

  public static DataStoreSearchRequest searchRequest(
      final Function<DataStoreSearchRequest.Builder, DataStoreObjectBuilder<DataStoreSearchRequest>>
          fn) {
    return requestBuilders.searchRequest(fn);
  }
}
