/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients;

import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.util.DataStoreObjectBuilder;
import io.camunda.zeebe.util.Either;
import java.util.function.Function;

public interface DataStoreClient {

  <T> Either<Exception, DataStoreSearchResponse<T>> search(
      final DataStoreSearchRequest searchRequest, final Class<T> documentClass);

  <T> Either<Exception, DataStoreSearchResponse<T>> search(
      final Function<DataStoreSearchRequest.Builder, DataStoreObjectBuilder<DataStoreSearchRequest>>
          fn,
      final Class<T> documentClass);
}
