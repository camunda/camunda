/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionRequirementsSearchQueryRequestStrictContract;
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
public class GeneratedDecisionRequirementsController {

  private final DecisionRequirementsServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedDecisionRequirementsController(
      final DecisionRequirementsServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/decision-requirements/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchDecisionRequirements(
      @RequestBody(required = false)
          final GeneratedDecisionRequirementsSearchQueryRequestStrictContract
              decisionRequirementsSearchQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.searchDecisionRequirements(
        decisionRequirementsSearchQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/decision-requirements/{decisionRequirementsKey}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getDecisionRequirements(
      @PathVariable("decisionRequirementsKey") final String decisionRequirementsKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getDecisionRequirements(decisionRequirementsKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/decision-requirements/{decisionRequirementsKey}/xml",
      produces = {"text/xml", "application/problem+json"})
  public ResponseEntity<Void> getDecisionRequirementsXML(
      @PathVariable("decisionRequirementsKey") final String decisionRequirementsKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getDecisionRequirementsXML(decisionRequirementsKey, authentication);
  }
}
