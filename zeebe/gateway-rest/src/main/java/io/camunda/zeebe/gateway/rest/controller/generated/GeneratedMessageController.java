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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessageCorrelationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessagePublicationRequestStrictContract;
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
public class GeneratedMessageController {

  private final MessageServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedMessageController(
      final MessageServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/messages/publication",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> publishMessage(
      @RequestBody
          final GeneratedMessagePublicationRequestStrictContract messagePublicationRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.publishMessage(messagePublicationRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/messages/correlation",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> correlateMessage(
      @RequestBody
          final GeneratedMessageCorrelationRequestStrictContract messageCorrelationRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.correlateMessage(messageCorrelationRequest, authentication);
  }
}
