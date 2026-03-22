/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentProcessInstanceStatisticsByErrorQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentResolutionRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentSearchQueryRequestStrictContract;
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
public class GeneratedIncidentController {

  private final IncidentServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedIncidentController(
      final IncidentServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/incidents/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchIncidents(
      @RequestBody(required = false)
          final GeneratedIncidentSearchQueryRequestStrictContract incidentSearchQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.searchIncidents(incidentSearchQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/incidents/{incidentKey}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getIncident(@PathVariable("incidentKey") final Long incidentKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getIncident(incidentKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/incidents/{incidentKey}/resolution",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> resolveIncident(
      @PathVariable("incidentKey") final Long incidentKey,
      @RequestBody(required = false)
          final GeneratedIncidentResolutionRequestStrictContract incidentResolutionRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.resolveIncident(incidentKey, incidentResolutionRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/incidents/statistics/process-instances-by-error",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getProcessInstanceStatisticsByError(
      @RequestBody(required = false)
          final GeneratedIncidentProcessInstanceStatisticsByErrorQueryStrictContract
              incidentProcessInstanceStatisticsByErrorQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getProcessInstanceStatisticsByError(
        incidentProcessInstanceStatisticsByErrorQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/incidents/statistics/process-instances-by-definition",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getProcessInstanceStatisticsByDefinition(
      @RequestBody
          final GeneratedIncidentProcessInstanceStatisticsByDefinitionQueryStrictContract
              incidentProcessInstanceStatisticsByDefinitionQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getProcessInstanceStatisticsByDefinition(
        incidentProcessInstanceStatisticsByDefinitionQuery, authentication);
  }
}
