/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.data.clients.DataStoreClient;
import io.camunda.service.auth.Authentication;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public abstract class ApiServices<T extends ApiServices<T>> {

  protected final DataStoreClient dataStoreClient;
  protected final Authentication authentication;

  protected ApiServices(
      final DataStoreClient dataStoreClient, final Authentication authentication) {
    this.dataStoreClient = dataStoreClient;
    this.authentication = authentication;
  }

  public abstract T withAuthentication(final Authentication authentication);

  public T withAuthentication(
      final Function<Authentication.Builder, DataStoreObjectBuilder<Authentication>> fn) {
    return withAuthentication(fn.apply(new Authentication.Builder()).build());
  }
}
