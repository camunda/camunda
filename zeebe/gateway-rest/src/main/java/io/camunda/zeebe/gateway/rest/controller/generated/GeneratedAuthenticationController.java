/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Profile("consolidated-auth")
@ConditionalOnSecondaryStorageEnabled
@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedAuthenticationController {

  private final AuthenticationServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedAuthenticationController(
      final AuthenticationServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/authentication/me",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getAuthentication() {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getAuthentication(authentication);
  }
}
