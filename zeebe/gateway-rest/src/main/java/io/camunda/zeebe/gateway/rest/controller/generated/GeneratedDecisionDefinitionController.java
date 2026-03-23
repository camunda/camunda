/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionDefinitionSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionEvaluationInstructionStrictContract;
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
public class GeneratedDecisionDefinitionController {

  private final DecisionDefinitionServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedDecisionDefinitionController(
      final DecisionDefinitionServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/decision-definitions/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchDecisionDefinitions(
      @RequestBody(required = false)
          final GeneratedDecisionDefinitionSearchQueryRequestStrictContract
              decisionDefinitionSearchQuery) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchDecisionDefinitions(decisionDefinitionSearchQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/decision-definitions/{decisionDefinitionKey}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getDecisionDefinition(
      @PathVariable("decisionDefinitionKey") final Long decisionDefinitionKey) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getDecisionDefinition(decisionDefinitionKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/decision-definitions/{decisionDefinitionKey}/xml",
      produces = {"text/xml", "application/problem+json"})
  public ResponseEntity<Void> getDecisionDefinitionXML(
      @PathVariable("decisionDefinitionKey") final Long decisionDefinitionKey) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getDecisionDefinitionXML(decisionDefinitionKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/decision-definitions/evaluation",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> evaluateDecision(
      @RequestBody
          final GeneratedDecisionEvaluationInstructionStrictContract
              decisionEvaluationInstruction) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.evaluateDecision(decisionEvaluationInstruction, authentication);
  }
}
