/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.service.UserServices;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public class BasicAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final UserServices userServices;

  public BasicAuthenticationConverter(final UserServices userServices) {
    this.userServices = userServices;
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return authentication instanceof UsernamePasswordAuthenticationToken;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    final var basicAuthentication = (UsernamePasswordAuthenticationToken) authentication;
    final var username = basicAuthentication.getName();
    final var user =
        userServices.withAuthentication(CamundaAuthentication.anonymous()).getUser(username);

    return CamundaAuthentication.of(
        b -> b.username(username).displayName(user.name()).email(user.email()));
  }
}
