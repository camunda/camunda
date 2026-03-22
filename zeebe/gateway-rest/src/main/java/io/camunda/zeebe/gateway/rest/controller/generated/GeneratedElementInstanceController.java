/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedElementInstanceSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSetVariableRequestStrictContract;
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
public class GeneratedElementInstanceController {

  private final ElementInstanceServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedElementInstanceController(
      final ElementInstanceServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/element-instances/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchElementInstances(
      @RequestBody(required = false)
          final GeneratedElementInstanceSearchQueryRequestStrictContract
              elementInstanceSearchQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.searchElementInstances(elementInstanceSearchQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/element-instances/{elementInstanceKey}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getElementInstance(
      @PathVariable("elementInstanceKey") final Long elementInstanceKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getElementInstance(elementInstanceKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/element-instances/{elementInstanceKey}/variables",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> createElementInstanceVariables(
      @PathVariable("elementInstanceKey") final Long elementInstanceKey,
      @RequestBody final GeneratedSetVariableRequestStrictContract setVariableRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.createElementInstanceVariables(
        elementInstanceKey, setVariableRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/element-instances/{elementInstanceKey}/incidents/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchElementInstanceIncidents(
      @PathVariable("elementInstanceKey") final Long elementInstanceKey,
      @RequestBody final GeneratedIncidentSearchQueryRequestStrictContract incidentSearchQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.searchElementInstanceIncidents(
        elementInstanceKey, incidentSearchQuery, authentication);
  }
}
