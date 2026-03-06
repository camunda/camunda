/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.CamundaAuthenticationConverter;
import io.camunda.auth.domain.spi.CamundaAuthenticationHolder;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
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
    if (holder.supports()) {
      final CamundaAuthentication cached = holder.get();
      if (cached != null) {
        return cached;
      }
    }

    final Authentication springAuth = SecurityContextHolder.getContext().getAuthentication();
    if (springAuth == null || !springAuth.isAuthenticated()) {
      return getAnonymousCamundaAuthentication();
    }

    if (converter.supports(springAuth)) {
      final CamundaAuthentication auth = converter.convert(springAuth);
      if (holder.supports()) {
        holder.set(auth);
      }
      return auth;
    }

    return getAnonymousCamundaAuthentication();
  }
}
