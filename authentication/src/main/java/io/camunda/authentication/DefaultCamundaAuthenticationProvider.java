/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.converter.CamundaAuthenticationFacade;
import io.camunda.authentication.converter.DeferredMembershipResolver;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationCache;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class DefaultCamundaAuthenticationProvider implements CamundaAuthenticationProvider {

  private final CamundaAuthenticationConverter<Authentication> converter;
  private final CamundaAuthenticationCache holder;
  private final DeferredMembershipResolver resolver;

  public DefaultCamundaAuthenticationProvider(
      final CamundaAuthenticationConverter<Authentication> converter,
      final CamundaAuthenticationCache holder,
      final DeferredMembershipResolver resolver) {
    this.converter = converter;
    this.holder = holder;
    this.resolver = resolver;
  }

  @Override
  public CamundaAuthentication getCamundaAuthentication() {
    final var authentication =
        Optional.ofNullable(getCamundaAuthenticationFromHolder()).orElseGet(this::convert);
    updateAuthenticationInHolder(authentication);
    return new CamundaAuthenticationFacade(authentication, resolver);
  }

  protected CamundaAuthentication getCamundaAuthenticationFromHolder() {
    return holder.getFromCache();
  }

  protected void updateAuthenticationInHolder(final CamundaAuthentication authentication) {
    holder.addOrUpdateInCache(authentication);
  }

  protected CamundaAuthentication convert() {
    return Optional.ofNullable(getSpringBasedAuthentication())
        .map(converter::convert)
        .orElseGet(CamundaAuthentication::none);
  }

  protected Authentication getSpringBasedAuthentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
