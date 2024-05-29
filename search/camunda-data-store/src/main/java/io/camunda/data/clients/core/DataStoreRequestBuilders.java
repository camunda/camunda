/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class DataStoreRequestBuilders {

  public static DataStoreSearchRequest.Builder searchRequest() {
    return new DataStoreSearchRequest.Builder();
  }

  public static DataStoreSearchRequest searchRequest(
      final Function<DataStoreSearchRequest.Builder, DataStoreObjectBuilder<DataStoreSearchRequest>>
          fn) {
    return fn.apply(searchRequest()).build();
  }
}
