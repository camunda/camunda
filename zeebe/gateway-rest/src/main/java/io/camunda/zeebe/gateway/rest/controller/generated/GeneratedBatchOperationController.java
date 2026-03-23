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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationItemSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationSearchQueryRequestStrictContract;
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
public class GeneratedBatchOperationController {

  private final BatchOperationServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedBatchOperationController(
      final BatchOperationServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/batch-operations/{batchOperationKey}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getBatchOperation(
      @PathVariable("batchOperationKey") final String batchOperationKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getBatchOperation(batchOperationKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/batch-operations/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchBatchOperations(
      @RequestBody(required = false) final GeneratedBatchOperationSearchQueryRequestStrictContract batchOperationSearchQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchBatchOperations(batchOperationSearchQuery, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/batch-operations/{batchOperationKey}/cancellation",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> cancelBatchOperation(
      @PathVariable("batchOperationKey") final String batchOperationKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.cancelBatchOperation(batchOperationKey, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/batch-operations/{batchOperationKey}/suspension",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> suspendBatchOperation(
      @PathVariable("batchOperationKey") final String batchOperationKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.suspendBatchOperation(batchOperationKey, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/batch-operations/{batchOperationKey}/resumption",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> resumeBatchOperation(
      @PathVariable("batchOperationKey") final String batchOperationKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.resumeBatchOperation(batchOperationKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/batch-operation-items/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchBatchOperationItems(
      @RequestBody(required = false) final GeneratedBatchOperationItemSearchQueryRequestStrictContract batchOperationItemSearchQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchBatchOperationItems(batchOperationItemSearchQuery, authentication);
  }
}
