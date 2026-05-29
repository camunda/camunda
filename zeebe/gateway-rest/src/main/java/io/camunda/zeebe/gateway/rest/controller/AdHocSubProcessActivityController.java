/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.protocol.model.AdHocSubProcessActivateActivitiesInstruction;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.AdHocSubProcessActivityServices;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/element-instances/ad-hoc-activities")
public class AdHocSubProcessActivityController {

  private final ServiceRegistry registry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public AdHocSubProcessActivityController(
      final ServiceRegistry registry, final CamundaAuthenticationProvider authenticationProvider) {
    this.registry = registry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/{adHocSubProcessInstanceKey}/activation")
  public CompletableFuture<ResponseEntity<Object>> activateAdHocSubProcessActivities(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String adHocSubProcessInstanceKey,
      @RequestBody final AdHocSubProcessActivateActivitiesInstruction activationRequest) {
    return RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, activationRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                activateActivities(
                    registry.adHocSubProcessActivityServices(physicalTenantId), mapped));
  }

  private CompletableFuture<ResponseEntity<Object>> activateActivities(
      final AdHocSubProcessActivityServices adHocSubProcessActivityServices,
      final AdHocSubProcessActivateActivitiesRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> adHocSubProcessActivityServices.activateActivities(request, authentication));
  }
}
