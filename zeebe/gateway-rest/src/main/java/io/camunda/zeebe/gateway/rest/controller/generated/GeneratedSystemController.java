/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedSystemController {

  private final SystemServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedSystemController(
      final SystemServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/system/usage-metrics",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getUsageMetrics(
      @RequestParam(name = "startTime", required = false) final String startTime,
      @RequestParam(name = "endTime", required = false) final String endTime,
      @RequestParam(name = "tenantId", required = false) final String tenantId,
      @RequestParam(name = "withTenants", required = false) final Boolean withTenants) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getUsageMetrics(
        startTime, endTime, tenantId, withTenants, authentication);
  }

  @RequiresSecondaryStorage
  @RequestMapping(
      method = RequestMethod.GET,
      value = "/system/configuration",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getSystemConfiguration() {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getSystemConfiguration(authentication);
  }
}
