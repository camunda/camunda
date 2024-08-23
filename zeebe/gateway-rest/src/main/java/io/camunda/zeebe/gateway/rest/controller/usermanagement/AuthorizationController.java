/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationAssignRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createAuthorization(
      @RequestBody final AuthorizationAssignRequest authorizationAssignRequest) {

    return RequestMapper.toAuthorizationAssignRequest(authorizationAssignRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::assignAuthorization);
  }

  private CompletableFuture<ResponseEntity<Object>> assignAuthorization(
      final AuthorizationAssignRequest authorizationAssignRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            authorizationServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createAuthorization(
                    authorizationAssignRequest.getOwnerKey(),
                    AuthorizationOwnerType.valueOf(
                        authorizationAssignRequest.getOwnerType().getValue()),
                    authorizationAssignRequest.getResourceKey(),
                    authorizationAssignRequest.getResourceType(),
                    authorizationAssignRequest.getPermissions()));
  }
}
