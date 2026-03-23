/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedCreateClusterVariableRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUpdateClusterVariableRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedClusterVariableController {

  private final ClusterVariableServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedClusterVariableController(
      final ClusterVariableServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/cluster-variables/global",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> createGlobalClusterVariable(
      @RequestBody
          final GeneratedCreateClusterVariableRequestStrictContract createClusterVariableRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.createGlobalClusterVariable(createClusterVariableRequest, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.POST,
      value = "/cluster-variables/tenants/{tenantId}",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> createTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId,
      @RequestBody
          final GeneratedCreateClusterVariableRequestStrictContract createClusterVariableRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.createTenantClusterVariable(
        tenantId, createClusterVariableRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/cluster-variables/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchClusterVariables(
      @RequestParam(name = "truncateValues", required = false) final Boolean truncateValues,
      @RequestBody(required = false)
          final GeneratedClusterVariableSearchQueryRequestStrictContract
              clusterVariableSearchQueryRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchClusterVariables(
        truncateValues, clusterVariableSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/cluster-variables/global/{name}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getGlobalClusterVariable(@PathVariable("name") final String name) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getGlobalClusterVariable(name, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/cluster-variables/global/{name}",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> updateGlobalClusterVariable(
      @PathVariable("name") final String name,
      @RequestBody
          final GeneratedUpdateClusterVariableRequestStrictContract updateClusterVariableRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.updateGlobalClusterVariable(
        name, updateClusterVariableRequest, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/cluster-variables/global/{name}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> deleteGlobalClusterVariable(@PathVariable("name") final String name) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteGlobalClusterVariable(name, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/cluster-variables/tenants/{tenantId}/{name}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId, @PathVariable("name") final String name) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getTenantClusterVariable(tenantId, name, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/cluster-variables/tenants/{tenantId}/{name}",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> updateTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("name") final String name,
      @RequestBody
          final GeneratedUpdateClusterVariableRequestStrictContract updateClusterVariableRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.updateTenantClusterVariable(
        tenantId, name, updateClusterVariableRequest, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/cluster-variables/tenants/{tenantId}/{name}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> deleteTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId, @PathVariable("name") final String name) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteTenantClusterVariable(tenantId, name, authentication);
  }
}
