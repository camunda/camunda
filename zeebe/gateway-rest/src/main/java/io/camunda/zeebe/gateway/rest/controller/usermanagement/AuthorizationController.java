/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.PatchAuthorizationRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/authorizations")
public class AuthorizationController {
  private final AuthorizationServices<AuthorizationRecord> authorizationServices;

  public AuthorizationController(
      final AuthorizationServices<AuthorizationRecord> authorizationServices) {
    this.authorizationServices = authorizationServices;
  }

  @PatchMapping(
      path = "/{ownerKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> patchAuthorization(
      @PathVariable final long ownerKey,
      @RequestBody final AuthorizationPatchRequest authorizationPatchRequest) {
    return RequestMapper.toAuthorizationPatchRequest(ownerKey, authorizationPatchRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::patchAuthorization);
  }

  private CompletableFuture<ResponseEntity<Object>> patchAuthorization(
      final PatchAuthorizationRequest patchAuthorizationRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            authorizationServices
                .withAuthentication(RequestMapper.getAuthentication())
                .patchAuthorization(patchAuthorizationRequest));
  }
}
