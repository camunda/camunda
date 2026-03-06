/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.converter;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.CamundaAuthenticationConverter;
import java.util.List;
import org.springframework.security.core.Authentication;

public class DelegatingAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final List<CamundaAuthenticationConverter<Authentication>> converters;

  public DelegatingAuthenticationConverter(
      final List<CamundaAuthenticationConverter<Authentication>> converters) {
    this.converters = List.copyOf(converters);
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return converters.stream().anyMatch(c -> c.supports(authentication));
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
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
