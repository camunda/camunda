/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core;

import io.camunda.data.clients.core.DataStoreSearchRequest.Builder;
import io.camunda.data.clients.util.DataStoreRequestBuildersDelegate;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public class ElasticsearchRequestBuilders implements DataStoreRequestBuildersDelegate {

  public DataStoreSearchRequest.Builder searchRequest() {
    return new ElasticsearchSearchRequest.Builder();
  }

  public DataStoreSearchRequest searchRequest(
      final Function<Builder, DataStoreObjectBuilder<DataStoreSearchRequest>> fn) {
    return fn.apply(searchRequest()).build();
  }
}
