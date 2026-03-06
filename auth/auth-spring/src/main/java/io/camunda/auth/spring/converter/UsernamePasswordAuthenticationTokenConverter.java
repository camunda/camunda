/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.converter;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.BasicAuthMembershipResolver;
import io.camunda.auth.domain.spi.CamundaAuthenticationConverter;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public class UsernamePasswordAuthenticationTokenConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final BasicAuthMembershipResolver basicAuthMembershipResolver;

  public UsernamePasswordAuthenticationTokenConverter(
      final BasicAuthMembershipResolver basicAuthMembershipResolver) {
    this.basicAuthMembershipResolver = basicAuthMembershipResolver;
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .filter(UsernamePasswordAuthenticationToken.class::isInstance)
        .isPresent();
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    return basicAuthMembershipResolver.resolveMemberships(authentication.getName());
  }
}
