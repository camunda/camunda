/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.model.mapper.RequestMapper;
import io.camunda.gateway.protocol.model.AdHocSubProcessActivateActivitiesInstruction;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.AdHocSubProcessActivityServices;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
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

  private final AdHocSubProcessActivityServices adHocSubProcessActivityServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public AdHocSubProcessActivityController(
      final AdHocSubProcessActivityServices adHocSubProcessActivityServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.adHocSubProcessActivityServices = adHocSubProcessActivityServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/{adHocSubProcessInstanceKey}/activation")
  public CompletableFuture<ResponseEntity<Object>> activateAdHocSubProcessActivities(
      @PathVariable final String adHocSubProcessInstanceKey,
      @RequestBody final AdHocSubProcessActivateActivitiesInstruction activationRequest) {
    return RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, activationRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::activateActivities);
  }

  private CompletableFuture<ResponseEntity<Object>> activateActivities(
      final AdHocSubProcessActivateActivitiesRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            adHocSubProcessActivityServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .activateActivities(request));
  }
}
