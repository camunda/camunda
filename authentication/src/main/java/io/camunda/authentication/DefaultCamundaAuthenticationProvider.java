/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class DefaultCamundaAuthenticationProvider implements CamundaAuthenticationProvider {

  private final CamundaAuthenticationHolder holder;
  private final CamundaAuthenticationConverter<Authentication> converter;

  public DefaultCamundaAuthenticationProvider(
      final CamundaAuthenticationHolder holder,
      final CamundaAuthenticationConverter<Authentication> converter) {
    this.holder = holder;
    this.converter = converter;
  }

  @Override
  public CamundaAuthentication getCamundaAuthentication() {
    final var springBasedAuthentication = SecurityContextHolder.getContext().getAuthentication();
    return Optional.ofNullable(getFromHolderIfPresent(springBasedAuthentication))
        .orElseGet(() -> convertAndSetInHolder(springBasedAuthentication));
  }

  @Override
  public void refresh() {
    final var springBasedAuthentication = SecurityContextHolder.getContext().getAuthentication();
    convertAndSetInHolder(springBasedAuthentication);
  }

  private CamundaAuthentication getFromHolderIfPresent(final Authentication authentication) {
    return Optional.ofNullable(authentication).map(principal -> holder.get()).orElse(null);
  }

  private CamundaAuthentication convertAndSetInHolder(final Authentication authentication) {
    final var result = authentication == null ? null : converter.convert(authentication);
    Optional.ofNullable(result).filter(a -> !a.isAnonymous()).ifPresent(p -> holder.set(result));
    return result;
  }
}
