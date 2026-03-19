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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceDeletionBatchOperationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeleteDecisionInstanceRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
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
public class GeneratedDecisionInstanceController {

  private final DecisionInstanceServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedDecisionInstanceController(
      final DecisionInstanceServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/decision-instances/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchDecisionInstances(
      @RequestBody(required = false)
          final GeneratedDecisionInstanceSearchQueryRequestStrictContract
              decisionInstanceSearchQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.searchDecisionInstances(decisionInstanceSearchQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/decision-instances/{decisionEvaluationInstanceKey}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getDecisionInstance(
      @PathVariable("decisionEvaluationInstanceKey") final String decisionEvaluationInstanceKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getDecisionInstance(decisionEvaluationInstanceKey, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/decision-instances/{decisionInstanceKey}/deletion",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> deleteDecisionInstance(
      @PathVariable("decisionInstanceKey") final String decisionInstanceKey,
      @RequestBody(required = false)
          final GeneratedDeleteDecisionInstanceRequestStrictContract
              deleteDecisionInstanceRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.deleteDecisionInstance(
        decisionInstanceKey, deleteDecisionInstanceRequest, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/decision-instances/deletion",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> deleteDecisionInstancesBatchOperation(
      @RequestBody
          final GeneratedDecisionInstanceDeletionBatchOperationRequestStrictContract
              decisionInstanceDeletionBatchOperationRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.deleteDecisionInstancesBatchOperation(
        decisionInstanceDeletionBatchOperationRequest, authentication);
  }
}
