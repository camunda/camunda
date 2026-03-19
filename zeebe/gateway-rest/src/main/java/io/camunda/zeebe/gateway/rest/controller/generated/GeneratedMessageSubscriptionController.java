/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedCorrelatedMessageSubscriptionSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessageSubscriptionSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedMessageSubscriptionController {

  private final MessageSubscriptionServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedMessageSubscriptionController(
      final MessageSubscriptionServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/message-subscriptions/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchMessageSubscriptions(
      @RequestBody(required = false)
          final GeneratedMessageSubscriptionSearchQueryRequestStrictContract
              messageSubscriptionSearchQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.searchMessageSubscriptions(
        messageSubscriptionSearchQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/correlated-message-subscriptions/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchCorrelatedMessageSubscriptions(
      @RequestBody(required = false)
          final GeneratedCorrelatedMessageSubscriptionSearchQueryRequestStrictContract
              correlatedMessageSubscriptionSearchQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.searchCorrelatedMessageSubscriptions(
        correlatedMessageSubscriptionSearchQuery, authentication);
  }
}
