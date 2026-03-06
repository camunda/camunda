/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.support;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.CamundaAuthenticationConverter;
import java.util.List;

/**
 * Delegating {@link CamundaAuthenticationConverter} that delegates to the first converter in the
 * chain that supports a given authentication object.
 *
 * @param <T> the type of authentication object to convert
 */
public class CamundaAuthenticationDelegatingConverter<T>
    implements CamundaAuthenticationConverter<T> {

  private final List<CamundaAuthenticationConverter<T>> converters;

  public CamundaAuthenticationDelegatingConverter(
      final List<CamundaAuthenticationConverter<T>> converters) {
    this.converters = List.copyOf(converters);
  }

  @Override
  public boolean supports(final T authentication) {
    return converters.stream().anyMatch(c -> c.supports(authentication));
  }

  @Override
  public CamundaAuthentication convert(final T authentication) {
    return converters.stream()
        .filter(c -> c.supports(authentication))
        .findFirst()
        .map(c -> c.convert(authentication))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No converter found for authentication type: "
                        + (authentication != null ? authentication.getClass().getName() : "null")));
  }
}
