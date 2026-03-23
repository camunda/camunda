/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedAuthorizationController {

  private final AuthorizationServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedAuthorizationController(
      final AuthorizationServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/authorizations",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> createAuthorization(
      @RequestBody final GeneratedAuthorizationRequestStrictContract authorizationRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.createAuthorization(authorizationRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/authorizations/{authorizationKey}",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> updateAuthorization(
      @PathVariable("authorizationKey") final Long authorizationKey,
      @RequestBody final GeneratedAuthorizationRequestStrictContract authorizationRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.updateAuthorization(authorizationKey, authorizationRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/authorizations/{authorizationKey}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getAuthorization(
      @PathVariable("authorizationKey") final Long authorizationKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getAuthorization(authorizationKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/authorizations/{authorizationKey}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> deleteAuthorization(
      @PathVariable("authorizationKey") final Long authorizationKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteAuthorization(authorizationKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/authorizations/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchAuthorizations(
      @RequestBody final GeneratedAuthorizationSearchQueryRequestStrictContract authorizationSearchQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchAuthorizations(authorizationSearchQuery, authentication);
  }
}
