/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionElementStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionInstanceStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionInstanceVersionStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionSearchQueryRequestStrictContract;
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
public class GeneratedProcessDefinitionController {

  private final ProcessDefinitionServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedProcessDefinitionController(
      final ProcessDefinitionServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-definitions/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchProcessDefinitions(
      @RequestBody(required = false)
          final GeneratedProcessDefinitionSearchQueryRequestStrictContract
              processDefinitionSearchQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.searchProcessDefinitions(processDefinitionSearchQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/process-definitions/{processDefinitionKey}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getProcessDefinition(
      @PathVariable("processDefinitionKey") final String processDefinitionKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getProcessDefinition(processDefinitionKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/process-definitions/{processDefinitionKey}/xml",
      produces = {"text/xml", "application/problem+json"})
  public ResponseEntity<Void> getProcessDefinitionXML(
      @PathVariable("processDefinitionKey") final String processDefinitionKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getProcessDefinitionXML(processDefinitionKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/process-definitions/{processDefinitionKey}/form",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getStartProcessForm(
      @PathVariable("processDefinitionKey") final String processDefinitionKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getStartProcessForm(processDefinitionKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-definitions/{processDefinitionKey}/statistics/element-instances",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getProcessDefinitionStatistics(
      @PathVariable("processDefinitionKey") final String processDefinitionKey,
      @RequestBody(required = false)
          final GeneratedProcessDefinitionElementStatisticsQueryStrictContract
              processDefinitionElementStatisticsQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getProcessDefinitionStatistics(
        processDefinitionKey, processDefinitionElementStatisticsQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-definitions/statistics/message-subscriptions",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getProcessDefinitionMessageSubscriptionStatistics(
      @RequestBody(required = false)
          final GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract
              processDefinitionMessageSubscriptionStatisticsQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getProcessDefinitionMessageSubscriptionStatistics(
        processDefinitionMessageSubscriptionStatisticsQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-definitions/statistics/process-instances",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getProcessDefinitionInstanceStatistics(
      @RequestBody(required = false)
          final GeneratedProcessDefinitionInstanceStatisticsQueryStrictContract
              processDefinitionInstanceStatisticsQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getProcessDefinitionInstanceStatistics(
        processDefinitionInstanceStatisticsQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/process-definitions/statistics/process-instances-by-version",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getProcessDefinitionInstanceVersionStatistics(
      @RequestBody
          final GeneratedProcessDefinitionInstanceVersionStatisticsQueryStrictContract
              processDefinitionInstanceVersionStatisticsQuery) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getProcessDefinitionInstanceVersionStatistics(
        processDefinitionInstanceVersionStatisticsQuery, authentication);
  }
}
