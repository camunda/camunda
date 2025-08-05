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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

public class UnprotectedCamundaAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  @Override
  public boolean supports(final Authentication authentication) {
    // 1) apiProtection == false and consolidated-auth profile used => authentication == null
    // 2) apiProtection == false and no auth profile used => authentication ==
    // AnonymousAuthenticationToken
    return authentication == null || authentication instanceof AnonymousAuthenticationToken;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    return CamundaAuthentication.anonymous();
  }
}
