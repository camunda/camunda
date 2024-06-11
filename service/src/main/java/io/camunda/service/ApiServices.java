/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public abstract class ApiServices<T extends ApiServices<T>> {

  protected final CamundaSearchClient searchClient;
  protected final Authentication authentication;
  protected final ServiceTransformers transformers;

  protected ApiServices(
      final CamundaSearchClient searchClient, final Authentication authentication) {
    this(searchClient, null, authentication);
  }

  protected ApiServices(
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    this.searchClient = searchClient;
    this.authentication = authentication;
    this.transformers = Objects.requireNonNullElse(transformers, new ServiceTransformers());
  }

  public abstract T withAuthentication(final Authentication authentication);

  public T withAuthentication(
      final Function<Authentication.Builder, ObjectBuilder<Authentication>> fn) {
    return withAuthentication(fn.apply(new Authentication.Builder()).build());
  }
}
