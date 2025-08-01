/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.authentication.exception.CamundaAuthenticationException;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

public class CamundaAuthenticationDelegatingConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaAuthenticationDelegatingConverter.class);

  private final List<CamundaAuthenticationConverter<Authentication>> converters;

  public CamundaAuthenticationDelegatingConverter(
      final List<CamundaAuthenticationConverter<Authentication>> converters) {
    this.converters = converters;
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return true;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    return getConverter(authentication).convert(authentication);
  }

  protected CamundaAuthenticationConverter<Authentication> getConverter(
      final Authentication authentication) {
    return converters.stream()
        .filter(c -> c.supports(authentication))
        .findFirst()
        .orElseThrow(
            () -> {
              final var message =
                  "Did not find a matching converter to convert a Spring Authentication '%s' to a Camunda Authentication"
                      .formatted(
                          Optional.ofNullable(authentication)
                              .map(Authentication::getClass)
                              .map(Class::getName)
                              .orElse("null"));
              LOG.error(message);
              return new CamundaAuthenticationException(message);
            });
  }
}
