/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import java.util.List;

public class CompositeCamundaAuthenticationConverter<T>
    implements CamundaAuthenticationConverter<T> {

  private final List<CamundaAuthenticationConverter<T>> converters;

  public CompositeCamundaAuthenticationConverter(
      final CamundaAuthenticationConverter<T> converter) {
    this(List.of(converter));
  }

  public CompositeCamundaAuthenticationConverter(
      final List<CamundaAuthenticationConverter<T>> converters) {
    this.converters = converters;
  }

  @Override
  public CamundaAuthentication convert(final T authentication) {
    return converters.stream()
        .filter(c -> c.supports(authentication))
        .findFirst()
        .map(c -> c.convert(authentication))
        .orElseThrow(() -> new IllegalArgumentException("unsupported authentication type"));
  }
}
